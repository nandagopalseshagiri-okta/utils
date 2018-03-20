 $a=[System.Reflection.Assembly]::LoadFile("C:\Program Files\Okta\Okta MFA Provider\bin\OktaMfaAdfs.dll")
 $file=[String]::Format("OktaMfaAdfs.AuthenticationAdapter, {0}", $a.GetName().FullName)

 Unregister-AdfsAuthenticationProvider -Name "OktaMfaAdfs"
 Register-AdfsAuthenticationProvider -Name "OktaMfaAdfs" -TypeName $file -ConfigurationFilePath "C:\Program Files\Okta\Okta MFA Provider\config\okta_adfs_adapter.json"
 