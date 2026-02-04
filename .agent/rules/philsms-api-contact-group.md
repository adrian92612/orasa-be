---
trigger: always_on
---

Profile API
PhilSMS Profile API allows you to retrieve your total remaining sms unit, used sms unit, and your profile information.
API Endpoint

Markup
https://dashboard.philsms.com/api/v3/me
Parameters
Parameter Required Description
Authorization Yes When calling our API, send your api token with the authentication type set as Bearer (Example: Authorization: Bearer {api_token})
Accept Yes Set to application/json
View sms unit
API Endpoint

Markup
https://dashboard.philsms.com/api/v3/balance
Example request
PHP
curl -X GET https://dashboard.philsms.com/api/v3/balance \
-H 'Authorization: Bearer 49|LNFe8WJ7CPtvl2mzowAB4ll4enbFR0XGgnQh2qWY' \
-H 'Content-Type: application/json' \
-H 'Accept: application/json' \
Returns
Returns a contact object if the request was successful.

JSON
{
"status": "success",
"data": "sms unit with all details",
}
If the request failed, an error object will be returned.

JSON
{
"status": "error",
"message" : "A human-readable description of the error."
}
View Profile
API Endpoint

Markup
https://dashboard.philsms.com/api/v3/me
Example request
PHP
curl -X GET https://dashboard.philsms.com/api/v3/me \
-H 'Authorization: Bearer 49|LNFe8WJ7CPtvl2mzowAB4ll4enbFR0XGgnQh2qWY' \
-H 'Content-Type: application/json' \
-H 'Accept: application/json' \
Returns
Returns a contact object if the request was successful.

JSON
{
"status": "success",
"data": "profile data with all details",
}
If the request failed, an error object will be returned.

JSON
{
"status": "error",
"message" : "A human-readable description of the error."
}
