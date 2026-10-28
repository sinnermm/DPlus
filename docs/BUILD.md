# 复现构建与测试

JDK17、Android SDK34/build-tools34、Gradle8.2.1。设置JAVA_HOME并在项目根创建不提交的local.properties：

```properties
sdk.dir=C:/your/Android/Sdk
```

```powershell
.\gradlew.bat assembleDebug lintDebug
New-Item -ItemType Directory -Force work-test | Out-Null
javac -encoding UTF-8 -d work-test app/src/main/java/com/yolo/dyfusion/core/*.java tests/CoreTest.java
java -cp work-test CoreTest
```

Android自检：`am instrument -w com.yolo.dyfusion/com.yolo.dyfusion.test.SelfTestInstrumentation`。当前期望44项。debug清单还含显式ConfigPatchInstrumentation，仅接受验证的模块配置JSON，不用于生产版发行。

SettingsProvider必须导出供宿主读取，但每次Binder调用校验调用UID的包名，只允许模块本身和三个抖音包。未知键/超范围值拒绝，报告最大32KiB。唯一Lint忽略是已审查的Provider路径，不整体关闭Lint。

本轮APK用本机Android debug签名，源码不包含用户密钥；其他机器的debug签名不同不能直接覆盖本次APK。源码归档排除构建缓存、local.properties和原始参考资料。
