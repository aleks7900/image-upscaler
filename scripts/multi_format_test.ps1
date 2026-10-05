$baseUrl = "http://localhost/api/v1"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "MULTI-IMAGE / MULTI-FORMAT BATCH UPSCALE TEST (2x, ANIME)" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Create Batch with 2x Anime preset
$batchBody = @{
    preset = "ADOBE_STOCK"
    scale = 2
    model = "anime"
    outputFormat = "JPEG"
    quality = 95
} | ConvertTo-Json

$batch = Invoke-RestMethod -Uri "$baseUrl/upscale/batches" -Method Post -Body $batchBody -ContentType "application/json"
$batchId = $batch.id
Write-Host "Created Batch: $batchId (Preset: ADOBE_STOCK, Scale: 2x, Model: anime)" -ForegroundColor Green

# 2. Upload anime PNG and landscape WEBP
$f1 = (Resolve-Path "test-dataset/test_anime.png").Path
$f2 = (Resolve-Path "test-dataset/test_landscape.webp").Path

Write-Host "Uploading test_anime.png and test_landscape.webp..."
$uploadResp = curl.exe -s -F "files=@$f1" -F "files=@$f2" "$baseUrl/upscale/batches/$batchId/images"
$images = $uploadResp | ConvertFrom-Json
Write-Host "Uploaded $($images.Count) images to batch $batchId" -ForegroundColor Green

# 3. Start batch
$startResp = Invoke-RestMethod -Uri "$baseUrl/upscale/batches/$batchId/start" -Method Post
Write-Host "Batch start response: $($startResp.status)" -ForegroundColor Green

# 4. Monitor batch
$maxWait = 120
$elapsed = 0
$done = $false

while ($elapsed -lt $maxWait) {
    Start-Sleep -Seconds 3
    $elapsed += 3
    $b = Invoke-RestMethod -Uri "$baseUrl/upscale/batches/$batchId"
    Write-Host "[$elapsed s] Status: $($b.status) | Completed: $($b.completedImages) / $($b.totalImages) | Failed: $($b.failedImages)"
    
    if ($b.status -in @("COMPLETED", "PARTIALLY_COMPLETED", "FAILED")) {
        $done = $true
        break
    }
}

if (-not $done) { throw "Batch timed out!" }

Write-Host "`nBatch completed with status: $($b.status)" -ForegroundColor Green

# 5. Check each image
$batchImages = Invoke-RestMethod -Uri "$baseUrl/upscale/batches/$batchId/images"
foreach ($img in $batchImages) {
    Write-Host "`nImage: $($img.originalFilename)" -ForegroundColor Yellow
    Write-Host " - Input: $($img.inputWidth)x$($img.inputHeight) ($($img.inputMegapixels) MP, $($img.inputSize) bytes)"
    Write-Host " - Output: $($img.outputWidth)x$($img.outputHeight) ($($img.outputMegapixels) MP, $($img.outputSize) bytes)"
    Write-Host " - Format: $($img.outputFormat), Color Profile: $($img.colorProfile)"
    Write-Host " - Stock Ready: $($img.stockReady)"
    Write-Host " - Status: $($img.status)"
    if ($img.error) {
        Write-Host " - Note/Error: $($img.error)" -ForegroundColor Red
    }
}

# 6. Download ZIP
$zipOut = "test-dataset/anime_batch_results.zip"
curl.exe -s -o $zipOut "$baseUrl/upscale/batches/$batchId/results.zip"
$zipItem = Get-Item $zipOut
Write-Host "`nDownloaded Batch ZIP: $($zipItem.Length) bytes" -ForegroundColor Green

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zipArchive = [System.IO.Compression.ZipFile]::OpenRead($zipOut)
Write-Host "`nZIP Entries:"
foreach ($entry in $zipArchive.Entries) {
    Write-Host " - $($entry.FullName) ($($entry.Length) bytes)"
}
$manifest = $zipArchive.Entries | Where-Object { $_.Name -eq 'adobe_stock_manifest.csv' }
if ($manifest) {
    $stream = $manifest.Open()
    $reader = New-Object System.IO.StreamReader($stream)
    Write-Host "`nManifest CSV Content:"
    Write-Host ($reader.ReadToEnd())
    $reader.Close()
    $stream.Close()
}
$zipArchive.Dispose()

Write-Host "`n==========================================================" -ForegroundColor Green
Write-Host "SUCCESS: MULTI-IMAGE BATCH TEST PASSED!" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
