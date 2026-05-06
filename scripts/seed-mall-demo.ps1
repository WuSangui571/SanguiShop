[CmdletBinding()]
param(
    [long]$ShopId = $(if ($env:SANGUI_DEMO_SHOP_ID) { [long]$env:SANGUI_DEMO_SHOP_ID } else { 1 }),
    [string]$UserBaseUrl = $(if ($env:SANGUI_DEMO_USER_BASE_URL) { $env:SANGUI_DEMO_USER_BASE_URL } else { "http://localhost:8101" }),
    [string]$ProductBaseUrl = $(if ($env:SANGUI_DEMO_PRODUCT_BASE_URL) { $env:SANGUI_DEMO_PRODUCT_BASE_URL } else { "http://localhost:8102" }),
    [string]$Username = $(if ($env:SANGUI_DEMO_USERNAME) { $env:SANGUI_DEMO_USERNAME } else { "mall_demo_user" }),
    [string]$Mobile = $(if ($env:SANGUI_DEMO_MOBILE) { $env:SANGUI_DEMO_MOBILE } else { "13800001001" }),
    [string]$Password = $(if ($env:SANGUI_DEMO_PASSWORD) { $env:SANGUI_DEMO_PASSWORD } else { "Passw0rd!" }),
    [string]$AdminUserId = $(if ($env:SANGUI_DEMO_ADMIN_USER_ID) { $env:SANGUI_DEMO_ADMIN_USER_ID } else { "dev-seed-admin" }),
    [string]$ProductName = $(if ($env:SANGUI_DEMO_PRODUCT_NAME) { $env:SANGUI_DEMO_PRODUCT_NAME } else { "Sangui Demo Trainer" })
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$DemoSkus = @(
    @{
        skuCode = "demo-trainer-42"
        skuName = "Size 42"
        priceCent = 59900
        availableStock = 20
    },
    @{
        skuCode = "demo-trainer-43"
        skuName = "Size 43"
        priceCent = 62900
        availableStock = 15
    }
)

function Join-ApiUrl {
    param(
        [Parameter(Mandatory = $true)][string]$BaseUrl,
        [Parameter(Mandatory = $true)][string]$Path
    )

    return $BaseUrl.TrimEnd("/") + "/" + $Path.TrimStart("/")
}

function Read-ApiError {
    param([Parameter(Mandatory = $true)]$ErrorRecord)

    if ($ErrorRecord.ErrorDetails -and $ErrorRecord.ErrorDetails.Message) {
        try {
            return $ErrorRecord.ErrorDetails.Message | ConvertFrom-Json
        } catch {
            return [pscustomobject]@{
                code = "HTTP_REQUEST_FAILED"
                message = $ErrorRecord.ErrorDetails.Message
                traceId = $null
            }
        }
    }

    $Exception = $ErrorRecord.Exception
    $response = $null
    if ($Exception.PSObject.Properties.Name -contains "Response") {
        $response = $Exception.Response
    }
    if ($response -and $response.GetResponseStream()) {
        $reader = [System.IO.StreamReader]::new($response.GetResponseStream())
        try {
            $body = $reader.ReadToEnd()
            if ($body) {
                try {
                    return $body | ConvertFrom-Json
                } catch {
                    return [pscustomobject]@{
                        code = "HTTP_REQUEST_FAILED"
                        message = $body
                        traceId = $null
                    }
                }
            }
        } finally {
            $reader.Dispose()
        }
    }

    return [pscustomobject]@{
        code = "HTTP_REQUEST_FAILED"
        message = $Exception.Message
        traceId = $null
    }
}

function Invoke-ApiJson {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Uri,
        [object]$Body = $null,
        [hashtable]$Headers = @{}
    )

    $requestHeaders = @{"X-Trace-Id" = "seed-" + [guid]::NewGuid().ToString("N")}
    foreach ($key in $Headers.Keys) {
        $requestHeaders[$key] = $Headers[$key]
    }

    $parameters = @{
        Method = $Method
        Uri = $Uri
        Headers = $requestHeaders
        ContentType = "application/json"
    }

    if ($null -ne $Body) {
        $parameters.Body = ($Body | ConvertTo-Json -Depth 8)
    }

    try {
        return Invoke-RestMethod @parameters
    } catch {
        $apiError = Read-ApiError $_
        throw [System.InvalidOperationException]::new(
            "API request failed: $Method $Uri code=$($apiError.code) message=$($apiError.message) traceId=$($apiError.traceId)"
        )
    }
}

function Invoke-ApiJsonAllowFailure {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Uri,
        [object]$Body = $null,
        [hashtable]$Headers = @{}
    )

    try {
        return @{
            ok = $true
            response = Invoke-ApiJson -Method $Method -Uri $Uri -Body $Body -Headers $Headers
            error = $null
        }
    } catch {
        $message = $_.Exception.Message
        $code = "HTTP_REQUEST_FAILED"
        if ($message -match "code=([^ ]+)") {
            $code = $Matches[1]
        }

        return @{
            ok = $false
            response = $null
            error = [pscustomobject]@{
                code = $code
                message = $message
            }
        }
    }
}

function Ensure-DemoUser {
    $registerBody = @{
        shopId = $ShopId
        username = $Username
        mobile = $Mobile
        password = $Password
    }

    $registerResult = Invoke-ApiJsonAllowFailure `
        -Method "POST" `
        -Uri (Join-ApiUrl $UserBaseUrl "/api/users/register") `
        -Body $registerBody

    if ($registerResult.ok) {
        Write-Host "Created mall demo user: username=$Username mobile=$Mobile userId=$($registerResult.response.data.userId)"
    } elseif ($registerResult.error.code -in @("USER_USERNAME_EXISTS", "USER_MOBILE_EXISTS")) {
        Write-Host "Mall demo user already exists; verifying login for username=$Username"
    } else {
        throw [System.InvalidOperationException]::new($registerResult.error.message)
    }

    $loginBody = @{
        shopId = $ShopId
        usernameOrMobile = $Username
        password = $Password
    }
    $loginResponse = Invoke-ApiJson `
        -Method "POST" `
        -Uri (Join-ApiUrl $UserBaseUrl "/api/users/login") `
        -Body $loginBody

    Write-Host "Verified mall demo login: username=$Username userId=$($loginResponse.data.userId)"
    return $loginResponse.data
}

function Get-ExistingDemoProduct {
    $listResponse = Invoke-ApiJson `
        -Method "GET" `
        -Uri (Join-ApiUrl $ProductBaseUrl "/api/products?page=1&size=100")

    return @($listResponse.data.items) | Where-Object { $_.productName -eq $ProductName } | Select-Object -First 1
}

function Assert-DemoProductMatches {
    param([Parameter(Mandatory = $true)]$ProductDetail)

    if ($ProductDetail.status -ne "active") {
        throw "Demo product exists but is not active: productId=$($ProductDetail.productId) status=$($ProductDetail.status)"
    }

    $existingByCode = @{}
    foreach ($sku in @($ProductDetail.skus)) {
        $existingByCode[$sku.skuCode] = $sku
    }

    foreach ($expected in $DemoSkus) {
        if (-not $existingByCode.ContainsKey($expected.skuCode)) {
            throw "Demo product exists but SKU is missing: skuCode=$($expected.skuCode)"
        }

        $actual = $existingByCode[$expected.skuCode]
        if ($actual.skuName -ne $expected.skuName `
                -or [long]$actual.priceCent -ne [long]$expected.priceCent `
                -or [long]$actual.availableStock -lt 1) {
            throw "Demo product SKU conflicts with expected seed payload: skuCode=$($expected.skuCode)"
        }
    }
}

function Ensure-DemoProduct {
    $headers = @{
        "X-Sangui-User-Id" = $AdminUserId
        "X-Sangui-Shop-Id" = [string]$ShopId
        "X-Sangui-Roles" = "ADMIN"
        "X-Sangui-Permissions" = ""
        "X-Sangui-Jwt-Id" = "dev-seed-local"
    }

    $existingProduct = Get-ExistingDemoProduct
    if ($existingProduct) {
        $detailResponse = Invoke-ApiJson `
            -Method "GET" `
            -Uri (Join-ApiUrl $ProductBaseUrl "/api/products/$($existingProduct.productId)")
        Assert-DemoProductMatches -ProductDetail $detailResponse.data
        Write-Host "Demo product already exists: productId=$($detailResponse.data.productId)"
        return $detailResponse.data
    }

    $createBody = @{
        productName = $ProductName
        productDescription = "Local demo product for the SanguiShop mall cart and checkout flow."
        skus = $DemoSkus
    }

    $createResponse = Invoke-ApiJson `
        -Method "POST" `
        -Uri (Join-ApiUrl $ProductBaseUrl "/api/admin/products") `
        -Body $createBody `
        -Headers $headers

    $productId = $createResponse.data.productId
    $publishResponse = Invoke-ApiJson `
        -Method "POST" `
        -Uri (Join-ApiUrl $ProductBaseUrl "/api/admin/products/$productId/publish") `
        -Headers $headers

    Write-Host "Created and published demo product: productId=$($publishResponse.data.productId)"
    return $publishResponse.data
}

Write-Host "Seeding SanguiShop mall demo data..."
Write-Host "User service: $UserBaseUrl"
Write-Host "Product service: $ProductBaseUrl"
Write-Host "Shop: $ShopId"

$user = Ensure-DemoUser
$product = Ensure-DemoProduct

Write-Host ""
Write-Host "Mall demo data ready."
Write-Host "User: username=$Username password=$Password shopId=$ShopId userId=$($user.userId)"
Write-Host "Product: productId=$($product.productId) name=$($product.productName)"
foreach ($sku in @($product.skus)) {
    Write-Host "SKU: skuId=$($sku.skuId) code=$($sku.skuCode) priceCent=$($sku.priceCent) availableStock=$($sku.availableStock)"
}
