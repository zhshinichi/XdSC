# 把 08_/09_ 开头的 SVG 导出为 PNG(2x) 与 PDF。
# 关键修复：chrome 是 GUI 子系统程序，必须用 Start-Process -Wait 等它真正退出后再读文件。
$chrome = 'C:\Program Files\Google\Chrome\Application\chrome.exe'
$dir = 'D:\software_work\diagrams'
$utf8 = New-Object System.Text.UTF8Encoding($false)
$ud = Join-Path $env:TEMP ("cr_chrome_" + [System.Guid]::NewGuid().ToString('N'))

function Invoke-Chrome($chromeArgs) {
  $full = @("--headless", "--disable-gpu", "--no-first-run", "--no-default-browser-check",
            "--virtual-time-budget=4000", "--user-data-dir=$ud") + $chromeArgs
  Start-Process -FilePath $chrome -ArgumentList $full -Wait -WindowStyle Hidden
}

$svgs = Get-ChildItem -LiteralPath $dir -Filter '0*.svg' | Where-Object { $_.Name -match '^(08|09)_' }
foreach ($f in $svgs) {
  $svg = [System.IO.File]::ReadAllText($f.FullName, $utf8)
  $w = 1100; $h = 700
  if ($svg -match 'width="(\d+)"\s+height="(\d+)"') { $w = [int]$Matches[1]; $h = [int]$Matches[2] }

  $html = "<!doctype html><html><head><meta charset=""utf-8""><style>@page{size:${w}px ${h}px;margin:0;}html,body{margin:0;padding:0;}svg{display:block;}</style></head><body>$svg</body></html>"
  $htmlPath = Join-Path $dir ("_wrap_" + $f.BaseName + ".html")
  [System.IO.File]::WriteAllText($htmlPath, $html, $utf8)
  $url = "file:///" + ($htmlPath -replace '\\','/')

  $png = Join-Path $dir ($f.BaseName + '.png')
  $pdf = Join-Path $dir ($f.BaseName + '.pdf')
  if (Test-Path $png) { Remove-Item $png -Force }
  if (Test-Path $pdf) { Remove-Item $pdf -Force }

  foreach ($attempt in 1..3) {
    Invoke-Chrome @("--force-device-scale-factor=2", "--hide-scrollbars",
                    "--window-size=$w,$h", "--screenshot=$png", $url)
    if ((Test-Path $png) -and ((Get-Item $png).Length -gt 3000)) { break }
  }
  foreach ($attempt in 1..3) {
    Invoke-Chrome @("--print-to-pdf=$pdf", $url)
    if ((Test-Path $pdf) -and ((Get-Item $pdf).Length -gt 3000)) { break }
  }

  Remove-Item $htmlPath -Force -ErrorAction SilentlyContinue
  $pngKB = if (Test-Path $png) { [int]((Get-Item $png).Length/1KB) } else { 0 }
  $pdfKB = if (Test-Path $pdf) { [int]((Get-Item $pdf).Length/1KB) } else { 0 }
  Write-Output ($f.BaseName + ": " + $w + "x" + $h + " -> PNG " + $pngKB + "KB, PDF " + $pdfKB + "KB")
}

Remove-Item $ud -Recurse -Force -ErrorAction SilentlyContinue
Write-Output 'done'
