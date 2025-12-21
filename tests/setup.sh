#!/bin/bash

set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &>/dev/null && pwd)
API_DIR="$SCRIPT_DIR/resources/apis"
POLICY_DIR="$SCRIPT_DIR/resources/policies/validateSOAP"
USERNAME="admin"
PASSWORD="admin"
CLIENT_PAYLOAD="$SCRIPT_DIR/resources/test_client.json"
CREDENTIALS_OUTPUT="$SCRIPT_DIR/credentials.json"
BASE_URL="https://localhost:9443"
TOKEN_ENDPOINT="${BASE_URL}/oauth2/token"
CLIENT_REGISTER_ENDPOINT="${BASE_URL}/client-registration/v0.17/register"
PUBLISHER_OPERATIONAL_POLICIES_ENDPOINT="${BASE_URL}/api/am/publisher/v4/operation-policies"
PUBLISHER_WSDL_ENDPOINT="${BASE_URL}/api/am/publisher/v4/apis/import-wsdl"
PUBLISHER_API_ENDPOINT="${BASE_URL}/api/am/publisher/v4/apis"
AUTH=$(echo -n "$USERNAME:$PASSWORD" | base64)

curl -s -k -X POST --fail \
  -H "Authorization: Basic $AUTH" \
  -H "Content-Type: application/json" \
  -d @"$CLIENT_PAYLOAD" \
  "${CLIENT_REGISTER_ENDPOINT}" >"$CREDENTIALS_OUTPUT"

echo "client created successfully and credentials stored in $CREDENTIALS_OUTPUT"

CLIENT_ID=$(cat $SCRIPT_DIR/credentials.json | jq -r '.clientId')
CLIENT_SECRET=$(cat $SCRIPT_DIR/credentials.json | jq -r '.clientSecret')

TOKEN=$(curl -k -d "grant_type=password&username=admin&password=${PASSWORD}&scope=apim:common_operation_policy_view apim:common_operation_policy_manage apim:api_view apim:api_create apim:api_publish apim:api_manage" -u "${CLIENT_ID}:${CLIENT_SECRET}" "${TOKEN_ENDPOINT}" -f -s | jq -r '.access_token')
if [ -z $TOKEN ]; then echo "no token received! Abort!" exit 1; fi

j2=$(ls $POLICY_DIR/*.j2)
json=$(ls $POLICY_DIR/*.json)
policy_response=$(curl -k --fail -H "Authorization: Bearer ${TOKEN}" -H "Content-Type: multipart/form-data" -F "policySpecFile=@${json}" -F "synapsePolicyDefinitionFile=@${j2}" "${PUBLISHER_OPERATIONAL_POLICIES_ENDPOINT}")
policy_id=$(echo "$policy_response" | jq -r '.id')

### diplomdaten-single #########################################################
resp=$(curl -k --fail -H "Authorization: Bearer $TOKEN" \
  -F file=@"$API_DIR/diplomdaten-single/DiplomdatenWebServiceAllinone.wsdl" \
  -F additionalProperties=@"$API_DIR/diplomdaten-single/data.json" \
  "${PUBLISHER_WSDL_ENDPOINT}")

api_id=$(echo "$resp" | jq -r ".id")
apiDefinition=$(curl -k --fail -H "Authorization: Bearer $TOKEN" "${PUBLISHER_API_ENDPOINT}/$api_id")
updatePayload=$(echo $apiDefinition | jq '.operations[0].operationPolicies.request += [{"policyName":"validateSOAP"}] | .operations[0].operationPolicies.response += [{"policyName":"validateSOAP"}]')
updatePayload=$(echo $updatePayload | jq '.operations[0].authType="None"')
UPDATE_RC=$(curl -X PUT -k -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json" -d "$updatePayload" "${PUBLISHER_API_ENDPOINT}/$api_id")
echo "Update API RC: $UPDATE_RC"
echo "sleeping 3 sec..."

description="{\"description\":\"revision for simple wsdl soap api - $(date +%s)\"}"
revisionId=$(curl -k -X POST -H "Content-Type: application/json" -H "Authorization: Bearer ${TOKEN}" --data "${description}" "${PUBLISHER_API_ENDPOINT}/$api_id/revisions" -f | jq -r '.id')
echo "REVISION ID: ${revisionId}"
# Deploy created revision to GW
DEPLOY_RC=$(curl -s -o /dev/null -w "%{http_code}" -k -X POST -H "Content-Type: application/json" -H "Authorization: Bearer ${TOKEN}" --data "[{\"name\":\"Default\",\"displayOnDevportal\":true,\"vhost\":\"localhost\"}]" "${PUBLISHER_API_ENDPOINT}/$api_id/deploy-revision?revisionId=$revisionId" -f)
echo "DEPLOY RC: $DEPLOY_RC"
#publish
PUBLISH_RC=$(curl -s -o /dev/null -w "%{http_code}" -k -X POST -H "Authorization: Bearer ${TOKEN}" "${PUBLISHER_API_ENDPOINT}/change-lifecycle?apiId=${api_id}&action=Publish")
echo "Publish RC: ${PUBLISH_RC}"

### diplomdaten-multi #########################################################
resp=$(curl -k --fail -H "Authorization: Bearer $TOKEN" \
  -F file=@"$API_DIR/diplomdaten-multi/diplomdaten-multi.zip;type=application/zip" \
  -F additionalProperties=@"$API_DIR/diplomdaten-multi/data.json" \
  "${PUBLISHER_WSDL_ENDPOINT}")

api_id=$(echo "$resp" | jq -r ".id")
apiDefinition=$(curl -k --fail -H "Authorization: Bearer $TOKEN" "${PUBLISHER_API_ENDPOINT}/$api_id")
updatePayload=$(echo $apiDefinition | jq '.operations[0].operationPolicies.request += [{"policyName":"validateSOAP"}] | .operations[0].operationPolicies.response += [{"policyName":"validateSOAP"}]')
updatePayload=$(echo $updatePayload | jq '.operations[0].authType="None"')
updatePayload=$(echo $updatePayload | jq '.wsdlInfo.type="ZIP"')
UPDATE_RC=$(curl -X PUT -k -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json" -d "$updatePayload" "${PUBLISHER_API_ENDPOINT}/$api_id")
echo "Update API RC: $UPDATE_RC"
echo "sleeping 3 sec..."

description="{\"description\":\"revision for simple wsdl soap api - $(date +%s)\"}"
revisionId=$(curl -k -X POST -H "Content-Type: application/json" -H "Authorization: Bearer ${TOKEN}" --data "${description}" "${PUBLISHER_API_ENDPOINT}/$api_id/revisions" -f | jq -r '.id')
echo "REVISION ID: ${revisionId}"
# Deploy created revision to GW
DEPLOY_RC=$(curl -s -o /dev/null -w "%{http_code}" -k -X POST -H "Content-Type: application/json" -H "Authorization: Bearer ${TOKEN}" --data "[{\"name\":\"Default\",\"displayOnDevportal\":true,\"vhost\":\"localhost\"}]" "${PUBLISHER_API_ENDPOINT}/$api_id/deploy-revision?revisionId=$revisionId" -f)
echo "DEPLOY RC: $DEPLOY_RC"
#publish
PUBLISH_RC=$(curl -s -o /dev/null -w "%{http_code}" -k -X POST -H "Authorization: Bearer ${TOKEN}" "${PUBLISHER_API_ENDPOINT}/change-lifecycle?apiId=${api_id}&action=Publish")
echo "Publish RC: ${PUBLISH_RC}"

### testservice-test1 #########################################################
resp=$(curl -k --fail -H "Authorization: Bearer $TOKEN" \
  -F file=@"$API_DIR/testservice-test1/testservice-test1.zip;type=application/zip" \
  -F additionalProperties=@"$API_DIR/testservice-test1/data.json" \
  "${PUBLISHER_WSDL_ENDPOINT}")

api_id=$(echo "$resp" | jq -r ".id")
apiDefinition=$(curl -k --fail -H "Authorization: Bearer $TOKEN" "${PUBLISHER_API_ENDPOINT}/$api_id")
updatePayload=$(echo $apiDefinition | jq '.operations[0].operationPolicies.request += [{"policyName":"validateSOAP"}] | .operations[0].operationPolicies.response += [{"policyName":"validateSOAP"}]')
updatePayload=$(echo $updatePayload | jq '.operations[0].authType="None"')
updatePayload=$(echo $updatePayload | jq '.wsdlInfo.type="ZIP"')
UPDATE_RC=$(curl -X PUT -k -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json" -d "$updatePayload" "${PUBLISHER_API_ENDPOINT}/$api_id")
echo "Update API RC: $UPDATE_RC"
echo "sleeping 3 sec..."

description="{\"description\":\"revision for simple wsdl soap api - $(date +%s)\"}"
revisionId=$(curl -k -X POST -H "Content-Type: application/json" -H "Authorization: Bearer ${TOKEN}" --data "${description}" "${PUBLISHER_API_ENDPOINT}/$api_id/revisions" -f | jq -r '.id')
echo "REVISION ID: ${revisionId}"
# Deploy created revision to GW
DEPLOY_RC=$(curl -s -o /dev/null -w "%{http_code}" -k -X POST -H "Content-Type: application/json" -H "Authorization: Bearer ${TOKEN}" --data "[{\"name\":\"Default\",\"displayOnDevportal\":true,\"vhost\":\"localhost\"}]" "${PUBLISHER_API_ENDPOINT}/$api_id/deploy-revision?revisionId=$revisionId" -f)
echo "DEPLOY RC: $DEPLOY_RC"
#publish
PUBLISH_RC=$(curl -s -o /dev/null -w "%{http_code}" -k -X POST -H "Authorization: Bearer ${TOKEN}" "${PUBLISHER_API_ENDPOINT}/change-lifecycle?apiId=${api_id}&action=Publish")
echo "Publish RC: ${PUBLISH_RC}"

### testservice-test2 #########################################################
resp=$(curl -k --fail -H "Authorization: Bearer $TOKEN" \
  -F file=@"$API_DIR/testservice-test2/testservice-test2.zip;type=application/zip" \
  -F additionalProperties=@"$API_DIR/testservice-test2/data.json" \
  "${PUBLISHER_WSDL_ENDPOINT}")

api_id=$(echo "$resp" | jq -r ".id")
apiDefinition=$(curl -k --fail -H "Authorization: Bearer $TOKEN" "${PUBLISHER_API_ENDPOINT}/$api_id")
updatePayload=$(echo $apiDefinition | jq '.operations[0].operationPolicies.request += [{"policyName":"validateSOAP"}] | .operations[0].operationPolicies.response += [{"policyName":"validateSOAP"}]')
updatePayload=$(echo $updatePayload | jq '.operations[0].authType="None"')
updatePayload=$(echo $updatePayload | jq '.wsdlInfo.type="ZIP"')
UPDATE_RC=$(curl -X PUT -k -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json" -d "$updatePayload" "${PUBLISHER_API_ENDPOINT}/$api_id")
echo "Update API RC: $UPDATE_RC"
echo "sleeping 3 sec..."

description="{\"description\":\"revision for simple wsdl soap api - $(date +%s)\"}"
revisionId=$(curl -k -X POST -H "Content-Type: application/json" -H "Authorization: Bearer ${TOKEN}" --data "${description}" "${PUBLISHER_API_ENDPOINT}/$api_id/revisions" -f | jq -r '.id')
echo "REVISION ID: ${revisionId}"
# Deploy created revision to GW
DEPLOY_RC=$(curl -s -o /dev/null -w "%{http_code}" -k -X POST -H "Content-Type: application/json" -H "Authorization: Bearer ${TOKEN}" --data "[{\"name\":\"Default\",\"displayOnDevportal\":true,\"vhost\":\"localhost\"}]" "${PUBLISHER_API_ENDPOINT}/$api_id/deploy-revision?revisionId=$revisionId" -f)
echo "DEPLOY RC: $DEPLOY_RC"
#publish
PUBLISH_RC=$(curl -s -o /dev/null -w "%{http_code}" -k -X POST -H "Authorization: Bearer ${TOKEN}" "${PUBLISHER_API_ENDPOINT}/change-lifecycle?apiId=${api_id}&action=Publish")
echo "Publish RC: ${PUBLISH_RC}"

### testservice-test3 #########################################################
resp=$(curl -k --fail -H "Authorization: Bearer $TOKEN" \
  -F file=@"$API_DIR/testservice-test3/testservice-test3.zip;type=application/zip" \
  -F additionalProperties=@"$API_DIR/testservice-test3/data.json" \
  "${PUBLISHER_WSDL_ENDPOINT}")

api_id=$(echo "$resp" | jq -r ".id")
apiDefinition=$(curl -k --fail -H "Authorization: Bearer $TOKEN" "${PUBLISHER_API_ENDPOINT}/$api_id")
updatePayload=$(echo $apiDefinition | jq '.operations[0].operationPolicies.request += [{"policyName":"validateSOAP"}] | .operations[0].operationPolicies.response += [{"policyName":"validateSOAP"}]')
updatePayload=$(echo $updatePayload | jq '.operations[0].authType="None"')
updatePayload=$(echo $updatePayload | jq '.wsdlInfo.type="ZIP"')
UPDATE_RC=$(curl -X PUT -k -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json" -d "$updatePayload" "${PUBLISHER_API_ENDPOINT}/$api_id")
echo "Update API RC: $UPDATE_RC"
echo "sleeping 3 sec..."

description="{\"description\":\"revision for simple wsdl soap api - $(date +%s)\"}"
revisionId=$(curl -k -X POST -H "Content-Type: application/json" -H "Authorization: Bearer ${TOKEN}" --data "${description}" "${PUBLISHER_API_ENDPOINT}/$api_id/revisions" -f | jq -r '.id')
echo "REVISION ID: ${revisionId}"
# Deploy created revision to GW
DEPLOY_RC=$(curl -s -o /dev/null -w "%{http_code}" -k -X POST -H "Content-Type: application/json" -H "Authorization: Bearer ${TOKEN}" --data "[{\"name\":\"Default\",\"displayOnDevportal\":true,\"vhost\":\"localhost\"}]" "${PUBLISHER_API_ENDPOINT}/$api_id/deploy-revision?revisionId=$revisionId" -f)
echo "DEPLOY RC: $DEPLOY_RC"
#publish
PUBLISH_RC=$(curl -s -o /dev/null -w "%{http_code}" -k -X POST -H "Authorization: Bearer ${TOKEN}" "${PUBLISHER_API_ENDPOINT}/change-lifecycle?apiId=${api_id}&action=Publish")
echo "Publish RC: ${PUBLISH_RC}"

### testservice-test4 #########################################################
resp=$(curl -k --fail -H "Authorization: Bearer $TOKEN" \
  -F file=@"$API_DIR/testservice-test4/testservice-test4.zip;type=application/zip" \
  -F additionalProperties=@"$API_DIR/testservice-test4/data.json" \
  "${PUBLISHER_WSDL_ENDPOINT}")

api_id=$(echo "$resp" | jq -r ".id")
apiDefinition=$(curl -k --fail -H "Authorization: Bearer $TOKEN" "${PUBLISHER_API_ENDPOINT}/$api_id")
updatePayload=$(echo $apiDefinition | jq '.operations[0].operationPolicies.request += [{"policyName":"validateSOAP"}] | .operations[0].operationPolicies.response += [{"policyName":"validateSOAP"}]')
updatePayload=$(echo $updatePayload | jq '.operations[0].authType="None"')
updatePayload=$(echo $updatePayload | jq '.wsdlInfo.type="ZIP"')
UPDATE_RC=$(curl -X PUT -k -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json" -d "$updatePayload" "${PUBLISHER_API_ENDPOINT}/$api_id")
echo "Update API RC: $UPDATE_RC"
echo "sleeping 3 sec..."

description="{\"description\":\"revision for simple wsdl soap api - $(date +%s)\"}"
revisionId=$(curl -k -X POST -H "Content-Type: application/json" -H "Authorization: Bearer ${TOKEN}" --data "${description}" "${PUBLISHER_API_ENDPOINT}/$api_id/revisions" -f | jq -r '.id')
echo "REVISION ID: ${revisionId}"
# Deploy created revision to GW
DEPLOY_RC=$(curl -s -o /dev/null -w "%{http_code}" -k -X POST -H "Content-Type: application/json" -H "Authorization: Bearer ${TOKEN}" --data "[{\"name\":\"Default\",\"displayOnDevportal\":true,\"vhost\":\"localhost\"}]" "${PUBLISHER_API_ENDPOINT}/$api_id/deploy-revision?revisionId=$revisionId" -f)
echo "DEPLOY RC: $DEPLOY_RC"
#publish
PUBLISH_RC=$(curl -s -o /dev/null -w "%{http_code}" -k -X POST -H "Authorization: Bearer ${TOKEN}" "${PUBLISHER_API_ENDPOINT}/change-lifecycle?apiId=${api_id}&action=Publish")
echo "Publish RC: ${PUBLISH_RC}"

### testservice-test5 #########################################################
resp=$(curl -k --fail -H "Authorization: Bearer $TOKEN" \
  -F file=@"$API_DIR/testservice-test5/testservice-test5.zip;type=application/zip" \
  -F additionalProperties=@"$API_DIR/testservice-test5/data.json" \
  "${PUBLISHER_WSDL_ENDPOINT}")

api_id=$(echo "$resp" | jq -r ".id")
apiDefinition=$(curl -k --fail -H "Authorization: Bearer $TOKEN" "${PUBLISHER_API_ENDPOINT}/$api_id")
updatePayload=$(echo $apiDefinition | jq '.operations[0].operationPolicies.request += [{"policyName":"validateSOAP"}] | .operations[0].operationPolicies.response += [{"policyName":"validateSOAP"}]')
updatePayload=$(echo $updatePayload | jq '.operations[0].authType="None"')
updatePayload=$(echo $updatePayload | jq '.wsdlInfo.type="ZIP"')
UPDATE_RC=$(curl -X PUT -k -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json" -d "$updatePayload" "${PUBLISHER_API_ENDPOINT}/$api_id")
echo "Update API RC: $UPDATE_RC"
echo "sleeping 3 sec..."

description="{\"description\":\"revision for simple wsdl soap api - $(date +%s)\"}"
revisionId=$(curl -k -X POST -H "Content-Type: application/json" -H "Authorization: Bearer ${TOKEN}" --data "${description}" "${PUBLISHER_API_ENDPOINT}/$api_id/revisions" -f | jq -r '.id')
echo "REVISION ID: ${revisionId}"
# Deploy created revision to GW
DEPLOY_RC=$(curl -s -o /dev/null -w "%{http_code}" -k -X POST -H "Content-Type: application/json" -H "Authorization: Bearer ${TOKEN}" --data "[{\"name\":\"Default\",\"displayOnDevportal\":true,\"vhost\":\"localhost\"}]" "${PUBLISHER_API_ENDPOINT}/$api_id/deploy-revision?revisionId=$revisionId" -f)
echo "DEPLOY RC: $DEPLOY_RC"
#publish
PUBLISH_RC=$(curl -s -o /dev/null -w "%{http_code}" -k -X POST -H "Authorization: Bearer ${TOKEN}" "${PUBLISHER_API_ENDPOINT}/change-lifecycle?apiId=${api_id}&action=Publish")
echo "Publish RC: ${PUBLISH_RC}"

### testservice-test6 #########################################################
resp=$(curl -k --fail -H "Authorization: Bearer $TOKEN" \
  -F file=@"$API_DIR/testservice-test6/testservice-test6.zip;type=application/zip" \
  -F additionalProperties=@"$API_DIR/testservice-test6/data.json" \
  "${PUBLISHER_WSDL_ENDPOINT}")

api_id=$(echo "$resp" | jq -r ".id")
apiDefinition=$(curl -k --fail -H "Authorization: Bearer $TOKEN" "${PUBLISHER_API_ENDPOINT}/$api_id")
updatePayload=$(echo $apiDefinition | jq '.operations[0].operationPolicies.request += [{"policyName":"validateSOAP"}] | .operations[0].operationPolicies.response += [{"policyName":"validateSOAP"}]')
updatePayload=$(echo $updatePayload | jq '.operations[0].authType="None"')
updatePayload=$(echo $updatePayload | jq '.wsdlInfo.type="ZIP"')
UPDATE_RC=$(curl -X PUT -k -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json" -d "$updatePayload" "${PUBLISHER_API_ENDPOINT}/$api_id")
echo "Update API RC: $UPDATE_RC"
echo "sleeping 3 sec..."

description="{\"description\":\"revision for simple wsdl soap api - $(date +%s)\"}"
revisionId=$(curl -k -X POST -H "Content-Type: application/json" -H "Authorization: Bearer ${TOKEN}" --data "${description}" "${PUBLISHER_API_ENDPOINT}/$api_id/revisions" -f | jq -r '.id')
echo "REVISION ID: ${revisionId}"
# Deploy created revision to GW
DEPLOY_RC=$(curl -s -o /dev/null -w "%{http_code}" -k -X POST -H "Content-Type: application/json" -H "Authorization: Bearer ${TOKEN}" --data "[{\"name\":\"Default\",\"displayOnDevportal\":true,\"vhost\":\"localhost\"}]" "${PUBLISHER_API_ENDPOINT}/$api_id/deploy-revision?revisionId=$revisionId" -f)
echo "DEPLOY RC: $DEPLOY_RC"
#publish
PUBLISH_RC=$(curl -s -o /dev/null -w "%{http_code}" -k -X POST -H "Authorization: Bearer ${TOKEN}" "${PUBLISHER_API_ENDPOINT}/change-lifecycle?apiId=${api_id}&action=Publish")
echo "Publish RC: ${PUBLISH_RC}"

### testservice-test7 #########################################################
resp=$(curl -k --fail -H "Authorization: Bearer $TOKEN" \
  -F file=@"$API_DIR/testservice-test7/testservice-test7.zip;type=application/zip" \
  -F additionalProperties=@"$API_DIR/testservice-test7/data.json" \
  "${PUBLISHER_WSDL_ENDPOINT}")

api_id=$(echo "$resp" | jq -r ".id")
apiDefinition=$(curl -k --fail -H "Authorization: Bearer $TOKEN" "${PUBLISHER_API_ENDPOINT}/$api_id")
updatePayload=$(echo $apiDefinition | jq '.operations[0].operationPolicies.request += [{"policyName":"validateSOAP"}] | .operations[0].operationPolicies.response += [{"policyName":"validateSOAP"}]')
updatePayload=$(echo $updatePayload | jq '.operations[0].authType="None"')
updatePayload=$(echo $updatePayload | jq '.wsdlInfo.type="ZIP"')
UPDATE_RC=$(curl -X PUT -k -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json" -d "$updatePayload" "${PUBLISHER_API_ENDPOINT}/$api_id")
echo "Update API RC: $UPDATE_RC"
echo "sleeping 3 sec..."

description="{\"description\":\"revision for simple wsdl soap api - $(date +%s)\"}"
revisionId=$(curl -k -X POST -H "Content-Type: application/json" -H "Authorization: Bearer ${TOKEN}" --data "${description}" "${PUBLISHER_API_ENDPOINT}/$api_id/revisions" -f | jq -r '.id')
echo "REVISION ID: ${revisionId}"
# Deploy created revision to GW
DEPLOY_RC=$(curl -s -o /dev/null -w "%{http_code}" -k -X POST -H "Content-Type: application/json" -H "Authorization: Bearer ${TOKEN}" --data "[{\"name\":\"Default\",\"displayOnDevportal\":true,\"vhost\":\"localhost\"}]" "${PUBLISHER_API_ENDPOINT}/$api_id/deploy-revision?revisionId=$revisionId" -f)
echo "DEPLOY RC: $DEPLOY_RC"
#publish
PUBLISH_RC=$(curl -s -o /dev/null -w "%{http_code}" -k -X POST -H "Authorization: Bearer ${TOKEN}" "${PUBLISHER_API_ENDPOINT}/change-lifecycle?apiId=${api_id}&action=Publish")
echo "Publish RC: ${PUBLISH_RC}"

### testservice-test8 #########################################################
resp=$(curl -k --fail -H "Authorization: Bearer $TOKEN" \
  -F file=@"$API_DIR/testservice-test8/testservice-test8.zip;type=application/zip" \
  -F additionalProperties=@"$API_DIR/testservice-test8/data.json" \
  "${PUBLISHER_WSDL_ENDPOINT}")

api_id=$(echo "$resp" | jq -r ".id")
apiDefinition=$(curl -k --fail -H "Authorization: Bearer $TOKEN" "${PUBLISHER_API_ENDPOINT}/$api_id")
updatePayload=$(echo $apiDefinition | jq '.operations[0].operationPolicies.request += [{"policyName":"validateSOAP"}] | .operations[0].operationPolicies.response += [{"policyName":"validateSOAP"}]')
updatePayload=$(echo $updatePayload | jq '.operations[0].authType="None"')
updatePayload=$(echo $updatePayload | jq '.wsdlInfo.type="ZIP"')
UPDATE_RC=$(curl -X PUT -k -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json" -d "$updatePayload" "${PUBLISHER_API_ENDPOINT}/$api_id")
echo "Update API RC: $UPDATE_RC"
echo "sleeping 3 sec..."

description="{\"description\":\"revision for simple wsdl soap api - $(date +%s)\"}"
revisionId=$(curl -k -X POST -H "Content-Type: application/json" -H "Authorization: Bearer ${TOKEN}" --data "${description}" "${PUBLISHER_API_ENDPOINT}/$api_id/revisions" -f | jq -r '.id')
echo "REVISION ID: ${revisionId}"
# Deploy created revision to GW
DEPLOY_RC=$(curl -s -o /dev/null -w "%{http_code}" -k -X POST -H "Content-Type: application/json" -H "Authorization: Bearer ${TOKEN}" --data "[{\"name\":\"Default\",\"displayOnDevportal\":true,\"vhost\":\"localhost\"}]" "${PUBLISHER_API_ENDPOINT}/$api_id/deploy-revision?revisionId=$revisionId" -f)
echo "DEPLOY RC: $DEPLOY_RC"
#publish
PUBLISH_RC=$(curl -s -o /dev/null -w "%{http_code}" -k -X POST -H "Authorization: Bearer ${TOKEN}" "${PUBLISHER_API_ENDPOINT}/change-lifecycle?apiId=${api_id}&action=Publish")
echo "Publish RC: ${PUBLISH_RC}"

### testservice-test9 #########################################################
resp=$(curl -k --fail -H "Authorization: Bearer $TOKEN" \
  -F file=@"$API_DIR/testservice-test9/testservice-test9.zip;type=application/zip" \
  -F additionalProperties=@"$API_DIR/testservice-test9/data.json" \
  "${PUBLISHER_WSDL_ENDPOINT}")

api_id=$(echo "$resp" | jq -r ".id")
apiDefinition=$(curl -k --fail -H "Authorization: Bearer $TOKEN" "${PUBLISHER_API_ENDPOINT}/$api_id")
updatePayload=$(echo $apiDefinition | jq '.operations[0].operationPolicies.request += [{"policyName":"validateSOAP"}] | .operations[0].operationPolicies.response += [{"policyName":"validateSOAP"}]')
updatePayload=$(echo $updatePayload | jq '.operations[0].authType="None"')
updatePayload=$(echo $updatePayload | jq '.wsdlInfo.type="ZIP"')
UPDATE_RC=$(curl -X PUT -k -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json" -d "$updatePayload" "${PUBLISHER_API_ENDPOINT}/$api_id")
echo "Update API RC: $UPDATE_RC"
echo "sleeping 3 sec..."

description="{\"description\":\"revision for simple wsdl soap api - $(date +%s)\"}"
revisionId=$(curl -k -X POST -H "Content-Type: application/json" -H "Authorization: Bearer ${TOKEN}" --data "${description}" "${PUBLISHER_API_ENDPOINT}/$api_id/revisions" -f | jq -r '.id')
echo "REVISION ID: ${revisionId}"
# Deploy created revision to GW
DEPLOY_RC=$(curl -s -o /dev/null -w "%{http_code}" -k -X POST -H "Content-Type: application/json" -H "Authorization: Bearer ${TOKEN}" --data "[{\"name\":\"Default\",\"displayOnDevportal\":true,\"vhost\":\"localhost\"}]" "${PUBLISHER_API_ENDPOINT}/$api_id/deploy-revision?revisionId=$revisionId" -f)
echo "DEPLOY RC: $DEPLOY_RC"
#publish
PUBLISH_RC=$(curl -s -o /dev/null -w "%{http_code}" -k -X POST -H "Authorization: Bearer ${TOKEN}" "${PUBLISHER_API_ENDPOINT}/change-lifecycle?apiId=${api_id}&action=Publish")
echo "Publish RC: ${PUBLISH_RC}"

### testservice-test10 #########################################################
resp=$(curl -k --fail -H "Authorization: Bearer $TOKEN" \
  -F file=@"$API_DIR/testservice-test10/testservice-test10.zip;type=application/zip" \
  -F additionalProperties=@"$API_DIR/testservice-test10/data.json" \
  "${PUBLISHER_WSDL_ENDPOINT}")

api_id=$(echo "$resp" | jq -r ".id")
apiDefinition=$(curl -k --fail -H "Authorization: Bearer $TOKEN" "${PUBLISHER_API_ENDPOINT}/$api_id")
updatePayload=$(echo $apiDefinition | jq '.operations[0].operationPolicies.request += [{"policyName":"validateSOAP"}] | .operations[0].operationPolicies.response += [{"policyName":"validateSOAP"}]')
updatePayload=$(echo $updatePayload | jq '.operations[0].authType="None"')
updatePayload=$(echo $updatePayload | jq '.wsdlInfo.type="ZIP"')
UPDATE_RC=$(curl -X PUT -k -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json" -d "$updatePayload" "${PUBLISHER_API_ENDPOINT}/$api_id")
echo "Update API RC: $UPDATE_RC"
echo "sleeping 3 sec..."

description="{\"description\":\"revision for simple wsdl soap api - $(date +%s)\"}"
revisionId=$(curl -k -X POST -H "Content-Type: application/json" -H "Authorization: Bearer ${TOKEN}" --data "${description}" "${PUBLISHER_API_ENDPOINT}/$api_id/revisions" -f | jq -r '.id')
echo "REVISION ID: ${revisionId}"
# Deploy created revision to GW
DEPLOY_RC=$(curl -s -o /dev/null -w "%{http_code}" -k -X POST -H "Content-Type: application/json" -H "Authorization: Bearer ${TOKEN}" --data "[{\"name\":\"Default\",\"displayOnDevportal\":true,\"vhost\":\"localhost\"}]" "${PUBLISHER_API_ENDPOINT}/$api_id/deploy-revision?revisionId=$revisionId" -f)
echo "DEPLOY RC: $DEPLOY_RC"
#publish
PUBLISH_RC=$(curl -s -o /dev/null -w "%{http_code}" -k -X POST -H "Authorization: Bearer ${TOKEN}" "${PUBLISHER_API_ENDPOINT}/change-lifecycle?apiId=${api_id}&action=Publish")
echo "Publish RC: ${PUBLISH_RC}"

echo "successfully created SOAP apis!"
