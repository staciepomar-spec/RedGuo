#!/system/bin/sh
# ReadGuo 熄屏守护（开机启动）
#
# 背景：宿主（红果）的「定时关闭」到点后，模块会先自己试着 su 一次；红果没被授予
# root 时这步必然失败（KernelSU 只对白名单 uid 暴露 su，exec 直接 ENOENT）。
# 于是模块把请求写进自己的触发目录，由这里代为熄屏 —— 这样 root 只需要给
# ReadGuo，红果保持无 root。
#
# 为什么不能直接跨进程调用：Android 11+ 的包可见性把「红果 → ReadGuo」的所有
# 显式通道（Provider / bindService / 显式广播 / 显式 Activity）全部拦掉，
# 而红果的清单里没有、也不可能加上指向 ReadGuo 的 <queries>。
#
# 触发目录用 inotify 监听（事件驱动，闲置不轮询、不耗电）。
# 卸载：删掉本文件与 /data/adb/rg_screenoff_hook.sh 后重启即可。

BB=/data/adb/ksu/bin/busybox
DIR=/data/user/0/com.phoenix.read/files/rg_guard
HOOK=/data/adb/rg_screenoff_hook.sh
LOG=/data/local/tmp/rg_screenoff.log

echo "$(date) guard start" >> "$LOG"

while true; do
    # 触发目录由宿主自己创建（红果至少启动过一次才有），没有就等
    while [ ! -d "$DIR" ]; do
        sleep 3
    done
    "$BB" inotifyd "$HOOK" "$DIR:n" >> "$LOG" 2>&1
    echo "$(date) inotifyd exited, restart" >> "$LOG"
    sleep 2
done
