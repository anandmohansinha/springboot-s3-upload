
# Spring Boot AWS S3 Pre-signed URLs

A basic Java 17 / Spring Boot 3 backend that generates pre-signed URLs. File bytes
go directly between the client and a private S3 bucket; they never pass through
the Spring Boot application.

## Project structure

```text
src/
  main/
    java/com/example/s3upload/
      S3UploadApplication.java
      config/
        S3Configuration.java
        S3Properties.java
      controller/
        FileController.java
      dto/
        DownloadUrlResponse.java
        UploadUrlRequest.java
        UploadUrlResponse.java
      model/
        FileMetadata.java
      service/
        FileMetadataService.java
    resources/
      application.yml
  test/
    java/com/example/s3upload/
      S3UploadApplicationTests.java
pom.xml
```

## AWS setup

Keep the S3 bucket private. The AWS identity used by this application needs at
least these permissions for the bucket's `uploads/*` prefix:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:GetObject"
      ],
      "Resource": "arn:aws:s3:::YOUR_BUCKET_NAME/uploads/*"
    }
  ]
}
```

The AWS SDK default credential provider chain is used. For local development,
set credentials and configuration with environment variables:

```bash
export AWS_ACCESS_KEY_ID="..."
export AWS_SECRET_ACCESS_KEY="..."
export AWS_REGION="us-east-1"
export AWS_S3_BUCKET="your-private-bucket-name"
```

PowerShell:

```powershell
$env:AWS_ACCESS_KEY_ID = "..."
$env:AWS_SECRET_ACCESS_KEY = "..."
$env:AWS_REGION = "us-east-1"
$env:AWS_S3_BUCKET = "your-private-bucket-name"
```

On AWS, prefer an IAM role attached to the runtime instead of static access
keys.

## Run

```bash
mvn spring-boot:run
```

## Generate and use an upload URL

Request:

```bash
curl -X POST "http://localhost:8080/api/v1/files/upload-url" \
  -H "Content-Type: application/json" \
  -d '{
    "fileName": "resume.pdf",
    "contentType": "application/pdf",
    "fileSize": 204800
  }'
```

Example response:

```json
{
  "fileId": "file-550e8400-e29b-41d4-a716-446655440000",
  "uploadUrl": "https://example-bucket.s3.amazonaws.com/...",
  "s3Key": "uploads/file-550e8400-e29b-41d4-a716-446655440000/resume.pdf",
  "expiresInSeconds": 900
}
```

Upload the file directly to S3. Use the exact content type and file size sent
when the URL was generated:

```bash
curl -X PUT "$UPLOAD_URL" \
  -H "Content-Type: application/pdf" \
  --upload-file "./resume.pdf"
```

This basic single-request upload supports files up to S3's 5 GiB `PutObject`
limit.

## Generate and use a download URL

Use the `fileId` returned by the upload URL endpoint:

```bash
curl "http://localhost:8080/api/v1/files/file-550e8400-e29b-41d4-a716-446655440000/download-url"
```

Example response:

```json
{
  "fileId": "file-550e8400-e29b-41d4-a716-446655440000",
  "downloadUrl": "https://example-bucket.s3.amazonaws.com/...",
  "expiresInSeconds": 300
}
```

Download directly from S3:

```bash
curl -L "$DOWNLOAD_URL" -o "downloaded-resume.pdf"
```

## Flow

1. The client sends file metadata to `POST /api/v1/files/upload-url`.
2. The backend creates a unique file ID and S3 key, stores the metadata in
   memory, and signs an S3 `PutObject` request for 15 minutes.
3. The client uploads the bytes directly to the private S3 bucket using the
   returned URL.
4. The client requests `GET /api/v1/files/{fileId}/download-url`.
5. The backend finds the S3 key in memory and signs an S3 `GetObject` request
   for 5 minutes.
6. The client downloads the bytes directly from S3.

Metadata is lost whenever the application restarts. This basic version also
does not verify that an upload completed before issuing a download URL. If a
browser calls S3 directly, configure the bucket's S3 CORS rules for the web
application's origin and the `PUT` method.

## Docker and Terraform deployment

The Terraform configuration creates:

- A private ECR repository named `springboot-demo`
- An Amazon Linux 2023 EC2 instance in the default VPC
- A security group allowing inbound TCP port `8080`
- An EC2 IAM role and instance profile
- ECR image-pull permissions
- S3 `PutObject` and `GetObject` permissions for an existing private bucket

EC2 User Data installs Docker, authenticates to ECR through the instance role,
pulls `springboot-demo:latest`, and starts the container. No AWS access keys are
stored in Terraform, the Docker image, or the EC2 instance.

The sandbox identity running Terraform still needs permission to create EC2,
ECR, IAM, and security-group resources.

### Project structure

```text
.
|-- src/
|-- pom.xml
|-- Dockerfile
|-- .dockerignore
`-- terraform/
    |-- main.tf
    |-- variables.tf
    |-- outputs.tf
    |-- user_data.sh
    `-- terraform.tfvars.example
```

### Prerequisites

Install and start:

- Java 17
- Maven
- Docker Desktop
- Terraform
- AWS CLI

For a temporary AWS sandbox, set all three temporary credential values in the
current PowerShell window:

```powershell
$env:AWS_ACCESS_KEY_ID = "YOUR_TEMP_ACCESS_KEY"
$env:AWS_SECRET_ACCESS_KEY = "YOUR_TEMP_SECRET_KEY"
$env:AWS_SESSION_TOKEN = "YOUR_TEMP_SESSION_TOKEN"
$env:AWS_REGION = "us-east-1"

aws sts get-caller-identity
```

### Configure Terraform

From the project root:

```powershell
Copy-Item terraform\terraform.tfvars.example terraform\terraform.tfvars
```

Edit `terraform/terraform.tfvars` and set the existing private S3 bucket:

```hcl
s3_bucket_name = "your-existing-private-s3-bucket"
allowed_cidr   = "0.0.0.0/0"
```

The bucket must be in `us-east-1` because the application signs S3 requests
for that region.

`0.0.0.0/0` is convenient for a short sandbox test but exposes port `8080`
publicly. Use your public IP with `/32` when possible.

### Initialize Terraform and create ECR

ECR must exist before Docker can push the image. Bootstrap only that resource:

```powershell
terraform -chdir=terraform init -reconfigure `
  -backend-config="bucket=YOUR_STATE_BUCKET" `
  -backend-config="key=springboot-demo/terraform.tfstate" `
  -backend-config="region=us-east-1" `
  -backend-config="encrypt=true"

terraform -chdir=terraform apply -target=aws_ecr_repository.app
```

Enter `yes` when prompted.

### Build the JAR and Docker image

```powershell
mvn clean package
docker build --platform linux/amd64 -t springboot-demo:latest .
```

Optionally test the image locally:

```powershell
docker run --rm -p 8080:8080 `
  -e AWS_ACCESS_KEY_ID=$env:AWS_ACCESS_KEY_ID `
  -e AWS_SECRET_ACCESS_KEY=$env:AWS_SECRET_ACCESS_KEY `
  -e AWS_SESSION_TOKEN=$env:AWS_SESSION_TOKEN `
  -e AWS_REGION=us-east-1 `
  -e AWS_S3_BUCKET=your-existing-private-s3-bucket `
  springboot-demo:latest
```

These credential variables are only for a local temporary-sandbox test. The
EC2 deployment does not pass them because the container uses the instance role.

### Push the image to ECR

```powershell
$ecrRepository = terraform -chdir=terraform output -raw ecr_repository_url
$ecrRegistry = $ecrRepository.Split("/")[0]

aws ecr get-login-password --region us-east-1 |
  docker login --username AWS --password-stdin $ecrRegistry

docker tag springboot-demo:latest "${ecrRepository}:latest"
docker push "${ecrRepository}:latest"
```

### Plan and deploy EC2

```powershell
terraform -chdir=terraform plan -out=tfplan
terraform -chdir=terraform apply tfplan
```

Display the results:

```powershell
terraform -chdir=terraform output ec2_public_ip
terraform -chdir=terraform output application_url
```

User Data normally needs a few minutes to install Docker and start the
container.

### Test the deployed application

```powershell
$baseUrl = terraform -chdir=terraform output -raw application_url
$file = Get-Item "C:\path\to\resume.pdf"

$body = @{
    fileName = $file.Name
    contentType = "application/pdf"
    fileSize = $file.Length
} | ConvertTo-Json

$upload = Invoke-RestMethod `
  -Method Post `
  -Uri "$baseUrl/api/v1/files/upload-url" `
  -ContentType "application/json" `
  -Body $body

$upload | ConvertTo-Json
```

Upload directly to S3:

```powershell
curl.exe -X PUT `
  -H "Content-Type: application/pdf" `
  --upload-file "$($file.FullName)" `
  "$($upload.uploadUrl)"
```

Generate and use the download URL:

```powershell
$download = Invoke-RestMethod `
  -Uri "$baseUrl/api/v1/files/$($upload.fileId)/download-url"

curl.exe -L "$($download.downloadUrl)" -o downloaded-resume.pdf
```

### Destroy sandbox resources

Run this before the one-hour sandbox expires:

```powershell
terraform -chdir=terraform destroy
```

Terraform uses `force_delete` on the ECR repository so the pushed image is also
removed during destroy.

## Automatic deployment with GitHub Actions

The workflow at `.github/workflows/deploy.yml` runs whenever code is pushed to
the `main` branch. It:

1. Builds the Spring Boot JAR with Java 17 and Maven.
2. Configures the temporary AWS sandbox credentials.
3. Initializes Terraform with remote state in S3.
4. Creates the ECR repository on the first deployment.
5. Builds and pushes Docker images tagged with the Git commit SHA and `latest`.
6. Runs `terraform plan` and `terraform apply`.
7. Replaces the EC2 instance because the commit-specific image tag changes its
   User Data.
8. Installs Docker on EC2 and runs the new image on port `8080`.

Persistent remote Terraform state is required because each GitHub-hosted runner
starts empty. The workflow stores state at:

```text
s3://TF_STATE_BUCKET/springboot-demo/terraform.tfstate
```

For a short sandbox, `TF_STATE_BUCKET` and `S3_BUCKET_NAME` can contain the same
existing private bucket name.

### GitHub repository secrets

Open the GitHub repository and go to:

```text
Settings -> Secrets and variables -> Actions -> New repository secret
```

Create these secrets:

| Secret | Required | Value |
|---|---:|---|
| `AWS_ACCESS_KEY_ID` | Yes | Temporary sandbox access key |
| `AWS_SECRET_ACCESS_KEY` | Yes | Temporary sandbox secret key |
| `AWS_SESSION_TOKEN` | Yes | Complete temporary sandbox session token |
| `TF_STATE_BUCKET` | Yes | Existing S3 bucket used for Terraform state |
| `S3_BUCKET_NAME` | Yes | Existing private bucket used by the application |
| `APP_ALLOWED_CIDR` | Yes | Source allowed on port 8080, such as `0.0.0.0/0` |
| `SSH_ALLOWED_CIDR` | Yes | Source allowed on port 22, preferably `YOUR_IP/32` |
| `EC2_SSH_PUBLIC_KEY` | No | Contents of an OpenSSH `.pub` key |

The AWS region is fixed to `us-east-1` in the workflow, so `AWS_REGION` does
not need to be a secret. Docker Hub secrets are also not needed because this
project uses Amazon ECR.

If SSH login is not needed, create `EC2_SSH_PUBLIC_KEY` with an empty value or
leave it absent. Port 22 is still opened by the requested security-group rule,
but no EC2 key pair is attached.

Sandbox credentials normally expire when the lab ends. At the start of every
new sandbox session, replace these three GitHub secrets before rerunning:

```text
AWS_ACCESS_KEY_ID
AWS_SECRET_ACCESS_KEY
AWS_SESSION_TOKEN
```

### Push the project to GitHub

If the directory is not already a Git repository:

```powershell
git init
git add .
git commit -m "Add automated EC2 deployment"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPOSITORY.git
git push -u origin main
```

For later deployments:

```powershell
git add .
git commit -m "Deploy application update"
git push origin main
```

Open the repository's **Actions** tab and select **Build and deploy to EC2**.
The final workflow step prints the new EC2 public IP and application URL.

### Test the GitHub deployment

Wait approximately two to five minutes after Terraform completes, then obtain
the URL from the workflow log:

```text
http://EC2_PUBLIC_IP:8080
```

The upload URL endpoint is:

```text
POST http://EC2_PUBLIC_IP:8080/api/v1/files/upload-url
```

Every successful push uses the Git commit SHA as the image tag. Terraform sees
the changed EC2 User Data and replaces the previous instance while preserving
the ECR repository, IAM resources, security group, and Terraform state.

### Sandbox limitations

The temporary sandbox identity must be allowed to create IAM roles, instance
profiles, ECR repositories, EC2 instances, key pairs, and security groups. If
the workflow returns `AccessDenied`, the sandbox provider has blocked one of
those operations and its permissions must be adjusted or the restricted
resource must be created manually.

Because Terraform state is stored in S3, do not delete the state object before
running `terraform destroy`. To destroy from your computer using the same
state:

```powershell
terraform -chdir=terraform init -reconfigure `
  -backend-config="bucket=YOUR_STATE_BUCKET" `
  -backend-config="key=springboot-demo/terraform.tfstate" `
  -backend-config="region=us-east-1" `
  -backend-config="encrypt=true"

$env:TF_VAR_s3_bucket_name = "YOUR_APPLICATION_BUCKET"
terraform -chdir=terraform destroy
```
