param (
    [Parameter(Mandatory=$true)][string]$b64
)
[System.Reflection.Assembly]::LoadWithPartialName("System.Security")
$b = [System.Convert]::FromBase64String($b64)
$e=[System.Security.Cryptography.DataProtectionScope]::LocalMachine
$plainBytes=[System.Security.Cryptography.ProtectedData]::UnProtect($b, $null, $e)

$text=[System.Text.Encoding]::UTF8.GetString($plainBytes)
echo $text
