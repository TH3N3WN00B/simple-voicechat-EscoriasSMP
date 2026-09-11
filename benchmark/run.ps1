$jdk = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
$src = "src"
$out = "out"
New-Item -ItemType Directory -Force -Path $out | Out-Null
& "$jdk\bin\javac.exe" -d $out "$src\*.java"
if ($LASTEXITCODE -ne 0) { Write-Error "javac failed"; exit 1 }
& "$jdk\bin\java.exe" -cp $out Main