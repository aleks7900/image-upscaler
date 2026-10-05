$baseUrl = "http://localhost/api/v1"
$testImg = "test-dataset/test_portrait.jpg"

Write-Host "=== 1. Checking GPU Status via Nginx ===" -ForegroundColor Cyan
$gpuInfo = Invoke-RestMethod -Uri "$baseUrl/system/gpu"
$gpuInfo | ConvertTo-Json
if (-not $gpuInfo.cudaAvailable) { throw "CUDA is not available!" }
Write-Host "CUDA Device: $($gpuInfo.device)" -ForegroundColor Green

Write-Host "`n=== 2. Creating Adobe Stock Batch ===" -ForegroundColor Cyan
$batchBody = @{
    preset = "ADOBE_STOCK"
    scale = 4
    model = "general"
    outputFormat = "JPEG"
    quality = 95
} | ConvertTo-Json

$batch = Invoke-RestMethod -Uri "$baseUrl/upscale/batches" -Method Post -Body $batchBody -ContentType "application/json"
$batchId = $batch.id
Write-Host "Batch ID: $batchId, Status: $($batch.status)" -ForegroundColor Green

Write-Host "`n=== 3. Uploading Test Image ===" -ForegroundColor Cyan
$filePath = (Resolve-Path $testImg).Path
$uploadJson = curl.exe -s -F "files=@$filePath" "$baseUrl/upscale/batches/$batchId/images"
$images = $uploadJson | ConvertFrom-Json
$image = $images[0]
$imageId = $image.id
Write-Host "Image ID: $imageId, Name: $($image.originalFilename), Dims: $($image.inputWidth)x$($image.inputHeight)" -ForegroundColor Green

Write-Host "`n=== 4. Starting Batch Processing ===" -ForegroundColor Cyan
$startResp = Invoke-RestMethod -Uri "$baseUrl/upscale/batches/$batchId/start" -Method Post
Write-Host "Batch started: $($startResp.status)" -ForegroundColor Green

Write-Host "`n=== 5. Monitoring GPU Processing ===" -ForegroundColor Cyan
$maxWait = 60
$elapsed = 0
$done = $false

while ($elapsed -lt $maxWait) {
    Start-Sleep -Seconds 2
    $elapsed += 2
    $bStatus = Invoke-RestMethod -Uri "$baseUrl/upscale/batches/$batchId"
    Write-Host "[$elapsed s] Batch Status: $($bStatus.status) | Completed: $($bStatus.completedImages)/$($bStatus.totalImages)"
    
    if ($bStatus.status -in @("COMPLETED", "PARTIALLY_COMPLETED", "FAILED")) {
        $done = $true
        break
    }
}

if (-not $done) { throw "Batch processing timed out!" }
if ($bStatus.status -ne "COMPLETED") { throw "Batch failed with status: $($bStatus.status)" }

Write-Host "`n=== 6. Verifying Adobe Stock Output ===" -ForegroundColor Cyan
$imgResult = Invoke-RestMethod -Uri "$baseUrl/upscale/images/$imageId"
$imgResult | ConvertTo-Json

if ($imgResult.status -ne "COMPLETED") { throw "Image status is not COMPLETED: $($imgResult.status)" }
if ($imgResult.outputWidth -ne 4096 -or $imgResult.outputHeight -ne 4096) { throw "Output resolution mismatch!" }
if (-not $imgResult.stockReady) { throw "Image is NOT Adobe Stock Ready!" }
if ($imgResult.outputFormat -ne "JPEG") { throw "Output format is not JPEG!" }
if ($imgResult.colorProfile -ne "sRGB") { throw "Color profile is not sRGB!" }

Write-Host "`n=== 7. Downloading Processed Result Image ===" -ForegroundColor Cyan
Invoke-WebRequest -Uri "$baseUrl/upscale/images/$imageId/result" -OutFile "test-dataset/output_result.jpg"
$dlFile = Get-Item "test-dataset/output_result.jpg"
Write-Host "Downloaded Result Image: $($dlFile.Length) bytes" -ForegroundColor Green

Write-Host "`n=== 8. Downloading Batch Results ZIP ===" -ForegroundColor Cyan
Invoke-WebRequest -Uri "$baseUrl/upscale/batches/$batchId/results.zip" -OutFile "test-dataset/results.zip"
$zipFile = Get-Item "test-dataset/results.zip"
Write-Host "Downloaded Results ZIP: $($zipFile.Length) bytes" -ForegroundColor Green

Write-Host "`n=======================================================" -ForegroundColor Green
Write-Host "SUCCESS: FULL END-TO-END CUDA ADOBE STOCK TEST PASSED!" -ForegroundColor Green
Write-Host "=======================================================" -ForegroundColor Green
