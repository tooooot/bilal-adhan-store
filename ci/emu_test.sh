#!/usr/bin/env bash
# اختبار حقيقي على محاكي أندرويد 14: المعالج، الأذان (صوت + شاشة فوق القفل) عبر منبّه فعلي، والودّجات.
P=net.wardyat.bilal; O=out; mkdir -p $O; R=$O/result.txt; : > $R
log() { echo "$@" | tee -a $R; }
shot() { adb exec-out screencap -p > "$O/$1.png"; }
adb wait-for-device; adb root >/dev/null; sleep 3; adb wait-for-device
adb shell settings put global hide_error_dialogs 1; adb shell settings put secure show_ime_with_hard_keyboard 0; adb shell settings put global window_animation_scale 0; adb shell settings put global transition_animation_scale 0; adb shell settings put global animator_duration_scale 0
adb install -r apk/app.apk | tee -a $R
adb shell pm grant $P android.permission.POST_NOTIFICATIONS
adb shell cmd appops set $P SCHEDULE_EXACT_ALARM allow
adb shell input keyevent 82   # فتح القفل البسيط
adb logcat -G 16M; adb logcat -c

tap_text() {  # يضغط عنصراً بنصّه عبر uiautomator (يقرأ شجرة WebView)
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml $O/ui.xml >/dev/null 2>&1
  python3 - "$1" <<'PY'
import re,sys
x=open('out/ui.xml',encoding='utf8',errors='ignore').read()
for t in sys.argv[1].split('|'):
    m=re.search(r'text="'+re.escape(t)+r'"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',x)
    if m:
        a=list(map(int,m.groups())); print((a[0]+a[2])//2,(a[1]+a[3])//2); sys.exit(0)
sys.exit(1)
PY
}
adb shell am start -n $P/.MainActivity >/dev/null; sleep 14; shot 01_wizard; sleep 8; shot 01b_wizard_later
log "== الرئيسية والإعدادات بعد تجاوز المعالج =="
adb shell am force-stop $P; sleep 1
alive() { [ -n "$(adb shell pidof $P | tr -d '\r')" ] || { log "التطبيق ميت — إعادة تشغيل (انهيار خدمات جوجل بالمحاكي)"; adb shell am start -n $P/.MainActivity --ez skip_wizard true >/dev/null; sleep 20; }; }
adb shell am start -n $P/.MainActivity --ez skip_wizard true >/dev/null; sleep 12; shot 03_home_early; sleep 12; alive; shot 03_home
adb shell pidof $P | tee -a $R
alive; adb shell input tap 135 2250; sleep 3; shot 04_settings
adb shell input swipe 540 1800 540 600 400; sleep 2; shot 04b_settings_scrolled
adb shell input tap 945 2250; sleep 2; shot 04c_home_again
alive; adb shell input tap 675 2250; sleep 3; shot 04d_qibla
adb shell input tap 405 2250; sleep 3; shot 04e_ramadan

adb logcat -d -v time > $O/logcat_phase1.txt
log "== أذان فعلي عبر منبّه =="
adb shell am force-stop $P; sleep 1
UID_=$(adb shell stat -c %u /data/data/$P | tr -d '\r')
NOW=$(adb shell date +%s | tr -d '\r'); T=$(( (NOW + 40) * 1000 ))
python3 - "$T" <<'PY'
import sys,json,html
t=int(sys.argv[1]); H=3600000
items=[{"t":t-6*H,"n":"fajr","l":"الفجر","k":"p","a":1,"s":0},{"t":t-5*H,"n":"sunrise","l":"الشروق","k":"sun","a":0,"s":0},{"t":t,"n":"dhuhr","l":"الظهر","k":"p","a":1,"s":0},{"t":t+H,"n":"asr","l":"العصر","k":"p","a":1,"s":0},{"t":t+2*H,"n":"maghrib","l":"المغرب","k":"p","a":1,"s":0},{"t":t+3*H,"n":"isha","l":"العشاء","k":"p","a":1,"s":0}]
open('prefs.xml','w',encoding='utf8').write('<?xml version=\'1.0\' encoding=\'utf-8\' standalone=\'yes\' ?>\n<map>\n<string name="items">%s</string>\n<string name="sound">adhan_haram</string>\n<string name="theme">dark</string>\n<boolean name="boost" value="true" />\n</map>\n'%html.escape(json.dumps(items,ensure_ascii=False)))
PY
adb push prefs.xml /data/data/$P/shared_prefs/bilal.xml >/dev/null
adb shell chown $UID_:$UID_ /data/data/$P/shared_prefs/bilal.xml; adb shell chmod 660 /data/data/$P/shared_prefs/bilal.xml; adb shell restorecon /data/data/$P/shared_prefs/bilal.xml
adb shell am broadcast -n $P/.BootReceiver -a android.intent.action.TIME_SET >/dev/null
adb shell dumpsys alarm | grep -A3 "$P" | head -12 | tee -a $R
adb shell input keyevent 26; sleep 1   # قفل الشاشة قبل الأذان
log "نائم حتى وقت المنبّه…"; sleep 50
adb shell dumpsys audio > $O/dumpsys_audio.txt   # قبل اللقطة: مقطع التكبيرتين قصير (5 ث) وينتهي سريعاً
shot 05_adhan_locked
adb shell uiautomator dump /sdcard/adhan_ui.xml >/dev/null 2>&1; adb pull /sdcard/adhan_ui.xml $O/adhan_ui.xml >/dev/null 2>&1; grep -o 'text="إيقاف الأذان"[^>]*bounds="[^"]*"' $O/adhan_ui.xml | tee -a $R; adb shell wm size | tee -a $R
adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity" | tee -a $R
if grep -E "state:started" $O/dumpsys_audio.txt | grep -q "USAGE_ALARM"; then log "الصوت: يعمل (USAGE_ALARM started) ✓"; else log "الصوت: لم يُرصد مشغّل ALARM نشط ✗"; fi
adb logcat -d | grep -E "Adhan|AdhanService|AndroidRuntime|FATAL" > $O/logcat_adhan.txt; tail -15 $O/logcat_adhan.txt | tee -a $R

log "== الودّجات =="
adb shell am force-stop $P; adb shell input keyevent 224; adb shell input keyevent 82
adb shell am broadcast -n $P/.BootReceiver -a android.intent.action.TIME_SET >/dev/null
wp() { adb shell am start -S -n $P/.WidgetPreviewActivity "${@:2}" >/dev/null; sleep 5; shot "$1"; }
adb shell cmd uimode night no; sleep 2; wp 06a_auto_day --es theme auto
adb shell cmd uimode night yes; sleep 3; wp 06b_auto_night --es theme auto
wp 06c_dark --es theme dark
wp 06d_light --es theme light
wp 06e_clear --es theme clear
wp 06f_custom --es theme dark --es wacc "'#2f9e6f'" --es wbg "'#12324a'" --ei walpha 70
adb logcat -d | grep -E "WidgetPreview|AndroidRuntime" | tail -5 | tee -a $R
cat $R
