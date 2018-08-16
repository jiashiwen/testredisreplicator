#redissyncer

## 用于redis同步

## 编译方法
mvn clean package

## 使用方法
```aidl
java -jar redissyncer-1.0.jar --source "redis://ip:port?authPassword=yourpassword" --target "redis://ip:port?authPassword=yourpassword"
```