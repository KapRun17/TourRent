param([Parameter(Mandatory=$true)][string]$Profile,[Parameter(Mandatory=$true)][string[]]$Images)
$ErrorActionPreference='Stop'
if ($Profile -notmatch '^[a-z0-9][a-z0-9-]+$') { throw 'Invalid Minikube profile name' }
foreach ($image in $Images) { if ($image -notmatch '^[a-zA-Z0-9./:_-]+$') { throw 'Invalid image reference' } }
# Transfer binary Docker archive through .NET streams. Windows PowerShell 5.1
# converts normal native pipelines to text and would corrupt a tar archive.
$source=New-Object System.Diagnostics.Process
$source.StartInfo.FileName='docker'
$source.StartInfo.Arguments='save '+($Images -join ' ')
$source.StartInfo.UseShellExecute=$false
$source.StartInfo.CreateNoWindow=$true
$source.StartInfo.RedirectStandardOutput=$true
$destination=New-Object System.Diagnostics.Process
$destination.StartInfo.FileName='docker'
$destination.StartInfo.Arguments="exec -i $Profile docker load"
$destination.StartInfo.UseShellExecute=$false
$destination.StartInfo.CreateNoWindow=$true
$destination.StartInfo.RedirectStandardInput=$true
try {
    $destination.Start() | Out-Null
    $source.Start() | Out-Null
    $source.StandardOutput.BaseStream.CopyTo($destination.StandardInput.BaseStream)
    $source.WaitForExit()
    $destination.StandardInput.Close()
    $destination.WaitForExit()
    if ($source.ExitCode -ne 0 -or $destination.ExitCode -ne 0) { throw 'Docker archive transfer failed' }
} finally { $source.Dispose();$destination.Dispose() }
