#!/usr/bin/env bash
# Seeds an EMULATOR (never a real phone) with sample contacts and call log rows so the app has
# something to show. Uses `adb shell content insert`, which the shell user may run on emulators.
#
# The WhatsApp rows below imitate what Android stores for a WhatsApp call that the platform
# attributes to WhatsApp's PhoneAccount. They are test fixtures for the emulator only.
#
# Usage: [RESET=1] scripts/seed-emulator.sh [adb-serial]
set -euo pipefail

ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
[[ $# -ge 1 ]] && ADB="$ADB -s $1"

if [[ "$($ADB shell getprop ro.kernel.qemu | tr -d '\r')" != "1" && "$($ADB shell getprop ro.boot.qemu | tr -d '\r')" != "1" ]]; then
  echo "Refusing to run: target is not an emulator." >&2
  exit 1
fi

now_ms=$(( $(date +%s) * 1000 ))
day=$(( 24 * 3600 * 1000 ))
hour=$(( 3600 * 1000 ))

TELEPHONY="com.android.phone/com.android.services.telephony.TelephonyConnectionService"
WHATSAPP="com.whatsapp/com.whatsapp.voipcalling.SelfManagedConnectionService"
OTHER_VOIP="org.example.voip/org.example.voip.CallService"

# `adb shell` joins its arguments into one remote command line, so every value is single-quoted
# inside a double-quoted remote command.
add_contact() { # name number
  local name="$1" number="$2"
  $ADB shell "content insert --uri content://com.android.contacts/raw_contacts --bind account_type:s: --bind account_name:s:" >/dev/null
  local id
  id=$($ADB shell "content query --uri content://com.android.contacts/raw_contacts --projection _id --sort '_id DESC'" \
    | head -1 | sed -E 's/.*_id=([0-9]+).*/\1/' | tr -d '\r')
  $ADB shell "content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:$id \
    --bind mimetype:s:vnd.android.cursor.item/name --bind 'data1:s:$name'" >/dev/null
  $ADB shell "content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:$id \
    --bind mimetype:s:vnd.android.cursor.item/phone_v2 --bind 'data1:s:$number' --bind data2:i:2" >/dev/null
  echo "contact: $name"
}

add_call() { # number type(1 in,2 out,3 missed,5 rejected) ago_ms duration_s component [features]
  local number="$1" type="$2" ago="$3" duration="$4" component="$5" features="${6:-0}"
  $ADB shell "content insert --uri content://call_log/calls --bind 'number:s:$number' --bind type:i:$type \
    --bind date:l:$(( now_ms - ago )) --bind duration:l:$duration --bind 'subscription_component_name:s:$component' \
    --bind features:i:$features --bind presentation:i:1 --bind new:i:0" >/dev/null
}

if [[ "${RESET:-0}" == "1" ]]; then
  # Emulator only: wipe previously seeded data first.
  $ADB shell "content delete --uri content://call_log/calls"
  $ADB shell "content delete --uri content://com.android.contacts/raw_contacts --where '_id>0'"
fi

add_contact "Rahul Sharma" "+91 98765 43210"
add_contact "Ankit Verma" "098111 22233"
add_contact "Priya Nair" "+919900112233"
add_contact "Mehul Shah" "9822001122"
add_contact "Aman Gupta" "+91 98200 33445"
add_contact "Rohit Iyer" "+91 99887 66554"

# Rahul: WhatsApp voice (outgoing, 4 days ago), phone incoming, WhatsApp video outgoing
add_call "+919876543210" 2 $(( 4 * day ))  751  "$WHATSAPP"
add_call "09876543210"   1 $(( 9 * day ))  252  "$TELEPHONY"
add_call "9876543210"    2 $(( 17 * day )) 1084 "$WHATSAPP" 1
# Duplicate-looking second call at a different time: must be kept
add_call "+919876543210" 3 $(( 20 * day )) 0    "$TELEPHONY"
# Ankit: phone call 6 hours ago, national format
add_call "+919811122233" 2 $(( 6 * hour )) 95   "$TELEPHONY"
# Priya: WhatsApp video 15 days ago; rejected call yesterday
add_call "+91 99001 12233" 2 $(( 15 * day )) 640 "$WHATSAPP" 1
add_call "+919900112233"   5 $(( 1 * day ))  0   "$TELEPHONY"
# Mehul: only incoming → "Never called"
add_call "+919822001122" 1 $(( 3 * day )) 61 "$TELEPHONY"
# Aman: phone 11 days ago; Rohit: phone 31 days ago, video-capable carrier call
add_call "+919820033445" 2 $(( 11 * day )) 300 "$TELEPHONY"
add_call "+919988766554" 2 $(( 31 * day )) 420 "$TELEPHONY" 1
# Unknown numbers, a different VoIP app (must NOT be labelled WhatsApp), and a very old call
add_call "+14155550123"  3 $(( 2 * day ))   0   "$TELEPHONY"
add_call "+442071838750" 1 $(( 5 * hour ))  42  "$OTHER_VOIP"
add_call "+919876543210" 2 $(( 900 * day )) 33  "$TELEPHONY"

echo "Seeded call log on emulator."
