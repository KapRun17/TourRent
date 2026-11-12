param([string]$BaseUrl='http://localhost:8078',[string]$AuthUrl='http://localhost:8180')
$ErrorActionPreference='Stop'
[Console]::OutputEncoding=[Text.Encoding]::UTF8
function Invoke-TourApi([string]$Method,[string]$Path,$Body=$null) {
    $parameters=@{Method=$Method;Uri="$BaseUrl$Path";Headers=@{Authorization="Bearer $script:Token";'X-Request-ID'=$script:RequestId};ContentType='application/json; charset=utf-8'}
    if ($null -ne $Body) {$parameters.Body=[Text.Encoding]::UTF8.GetBytes(($Body|ConvertTo-Json -Depth 8))}
    Invoke-RestMethod @parameters
}
$auth=Invoke-RestMethod -Method Post -Uri "$AuthUrl/realms/tourrent/protocol/openid-connect/token" -ContentType 'application/x-www-form-urlencoded' -Body @{grant_type='password';client_id='tourrent-web';username='demo';password='Demo12345!'}
$script:Token=$auth.access_token
$script:RequestId='demo-'+[guid]::NewGuid().ToString('N').Substring(0,12)
Write-Host "Request ID: $script:RequestId"
Write-Host '1. Каталог через Gateway и LoadBalancer'
Invoke-TourApi GET '/api/equipment' | Format-Table id,name,dailyPrice,stock
$start=(Get-Date).Date.AddDays(4).ToString('yyyy-MM-dd')
$end=(Get-Date).Date.AddDays(7).ToString('yyyy-MM-dd')
Write-Host '2. Создание аренды; синхронные FeignClient и RestClient'
$rental=Invoke-TourApi POST '/api/rentals' @{equipmentId=1;quantity=1;startDate=$start;endDate=$end;recipient='Прокофьев И.Р.';address='Москва, проспект Вернадского, 78'}
$rental | Format-List
Write-Host '3. SSE: CONFIRMED -> DELIVERY_CREATED -> IN_TRANSIT -> DELIVERED'
Write-Host 'Поток остаётся открыт для возврата. Ctrl+C завершает просмотр.'
curl.exe -N -H "Authorization: Bearer $script:Token" "$BaseUrl/api/rentals/$($rental.id)/events"
