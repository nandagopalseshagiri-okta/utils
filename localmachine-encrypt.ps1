param (
    [Parameter(Mandatory=$true)][string]$str
)
$ignored=[System.Reflection.Assembly]::LoadWithPartialName("System.Security")
$bytes=[System.Text.Encoding]::UTF8.GetBytes($str)
$e=[System.Security.Cryptography.DataProtectionScope]::LocalMachine
$ebytes=[System.Security.Cryptography.ProtectedData]::Protect($bytes, $null, $e)
$b = [System.Convert]::ToBase64String($ebytes)

echo $b
