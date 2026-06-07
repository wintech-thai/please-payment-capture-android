#!/bin/bash

# ==========================================
# 1. ตั้งค่าตัวแปร (แก้ไขให้ตรงกับของจริง)
# ==========================================
API_KEY="ae933e77-a3f4-4f1b-8125-69643698248b"
BASE_URL="https://api-dev.please-payment.com"
ENDPOINT="/api/PaymentRequest/org/gabx01/action/SubmitPaymentRequest/eab2eae2-ab83-4d49-bff6-a30226663d09" # ปรับเปลี่ยน Endpoint ให้ตรงกับในไฟล์ .rb จริงๆ

# ==========================================
# 2. สร้าง Payload (JSON Body)
# ==========================================
# ใช้ heredoc (EOF) เพื่อให้อ่านและแก้ไข JSON ได้ง่าย
JSON_BODY=$(cat <<EOF
{
  "PaymentAmount": 10.45,
  "RemainAmount": 0.00,
  "TxType": "PayIn",
  "SourceBankCode": "SCB"
}
EOF
)

# ==========================================
# 3. ยิง cURL Request
# ==========================================
echo "===== Using API KEY ====="
echo "Sending POST request to ${BASE_URL}${ENDPOINT}..."

curl -X POST "${BASE_URL}${ENDPOINT}" \
  -u "api:${API_KEY}" \
  -H "Content-Type: application/json" \
  -d "${JSON_BODY}" \
  -v

# หมายเหตุ: ใส่ -v (verbose) ไว้ให้เพื่อให้เห็น Header ตอนยิงจริง
# หากต้องการให้แสดงแค่ผลลัพธ์ (Response) ให้เปลี่ยน -v เป็น -s
