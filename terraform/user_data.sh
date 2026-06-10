#!/bin/bash
set -euxo pipefail

exec > >(tee /var/log/user-data.log | logger -t user-data -s 2>/dev/console) 2>&1

AWS_REGION="${aws_region}"
ECR_REGISTRY="${ecr_registry}"
IMAGE="${ecr_repository_url}:${image_tag}"
CONTAINER_NAME="${container_name}"

dnf install -y docker
systemctl enable --now docker

# Retry briefly because a sandbox deployment may push the image just after EC2 starts.
image_pulled=false
for attempt in $(seq 1 60); do
  if aws ecr get-login-password --region "$AWS_REGION" \
    | docker login --username AWS --password-stdin "$ECR_REGISTRY" \
    && docker pull "$IMAGE"; then
    image_pulled=true
    break
  fi

  echo "ECR login or image pull failed. Retry $attempt/60 in 10 seconds."
  sleep 10
done

if [ "$image_pulled" != "true" ]; then
  echo "Unable to pull $IMAGE after 60 attempts."
  exit 1
fi

docker rm -f "$CONTAINER_NAME" || true

docker run -d \
  --name "$CONTAINER_NAME" \
  --restart unless-stopped \
  -p ${application_port}:8080 \
  -e AWS_REGION="$AWS_REGION" \
  -e AWS_S3_BUCKET="${s3_bucket_name}" \
  "$IMAGE"
