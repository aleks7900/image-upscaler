$files = Get-Item 'test-dataset/test_portrait_upscaled.jpg', 'test-dataset/batch_results.zip'
foreach ($f in $files) {
    Write-Host "File: $($f.Name), Size: $($f.Length) bytes"
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead('test-dataset/batch_results.zip')
Write-Host "`nZIP Entries:"
foreach ($entry in $zip.Entries) {
    Write-Host " - $($entry.FullName) ($($entry.Length) bytes)"
}

$manifest = $zip.Entries | Where-Object { $_.Name -eq 'adobe_stock_manifest.csv' }
if ($manifest) {
    $stream = $manifest.Open()
    $reader = New-Object System.IO.StreamReader($stream)
    Write-Host "`nManifest CSV Content:"
    Write-Host ($reader.ReadToEnd())
    $reader.Close()
    $stream.Close()
}
$zip.Dispose()
