#!/system/bin/sh
# inotifyd 回调。注意 busybox 的实参顺序：
#   $1 = 实际发生的动作字母（n=新建子文件）
#   $2 = 被监听的路径（这里是触发目录）
#   $3 = 子文件名
LOG=/data/local/tmp/rg_screenoff.log

case "$3" in
    *.req)
        rm -f "$2/$3"
        /system/bin/sh /system/bin/input keyevent 26
        echo "$(date) screen off ($1 $3)" >> "$LOG"
        ;;
esac
