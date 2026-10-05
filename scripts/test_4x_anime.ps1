$batch = Invoke-RestMethod -Uri "http://localhost/api/v1/upscale/batches" -Method Post -Body '{"preset":"ADOBE_STOCK","scale":4,"model":"anime","outputFormat":"JPEG","quality":95}' -ContentType "application/json"
$bId = $batch.id
Write-Host "Created 4x Anime Batch: $bId" -ForegroundColor Cyan

curl.exe -s -F "files=@test-dataset/test_anime.png" "http://localhost/api/v1/upscale/batches/$bId/images" | Out-Null
curl.exe -s -F "files=@test-dataset/test_landscape.webp" "http://localhost/api/v1/upscale/batches/$bId/images" | Out-Null
Write-Host "Uploaded anime PNG and landscape WEBP" -ForegroundColor Green

Invoke-RestMethod -Uri "http://localhost/api/v1/upscale/batches/$bId/start" -Method Post | Out-Null
Write-Host "Started batch processing..." -ForegroundColor Green

$done = $false
for ($i = 0; $i -lt 30; $i++) {
    Start-Sleep -Seconds 3
    $res = Invoke-RestMethod -Uri "http://localhost/api/v1/upscale/batches/$bId"
    Write-Host "[$(($i + 1) * 3) s] Status: $($res.status) | Completed: $($res.completedImages)/$($res.totalImages)"
    if ($res.status -in @("COMPLETED", "PARTIALLY_COMPLETED", "FAILED")) {
        $done = $true
        break
    }
}

Write-Host "`nBatch Result Verification:" -ForegroundColor Cyan
$imgs = Invoke-RestMethod -Uri "http://localhost/api/v1/upscale/batches/$bId/images"
foreach ($im in $imgs) {
    Write-Host " - $($im.originalFilename): Status: $($im.status) | $($im.outputWidth)x$($im.outputHeight) ($($im.outputMegapixels) MP, $($im.outputSize) bytes) | StockReady: $($im.stockReady) | Format: $($im.outputFormat) | Profile: $($im.colorProfile)" -ForegroundColor Green
}
