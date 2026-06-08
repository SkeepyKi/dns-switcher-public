# 🛡️ DNS Switcher

Автоматическое переключение Private DNS при запуске определённых приложений.

## Зачем?

Некоторые игры (Brawl Stars и др.) плохо работают с Private DNS — высокий пинг, обрывы соединения. DNS Switcher **автоматически отключает DNS** при запуске таких приложений и **включает обратно** при выходе из них.

## Возможности

- ⚡ Автоматическое переключение DNS по активному приложению
- 📱 Поддержка нескольких приложений-исключений
- 🔍 Удобный поиск и выбор приложений с иконками
- 🔲 Quick Settings тайл в шторке
- 🔄 Автозапуск после перезагрузки
- 📋 Встроенный визард настройки без ПК (через Brevent)
- 🔋 Минимальное потребление батареи

## Поддерживаемые версии

- **Минимум:** Android 11 (API 30)
- **Целевая:** Android 16 (API 36)

## Скачать

📥 **[Скачать APK (последняя версия)](https://github.com/SkeepyKi/dns-switcher-public/releases/latest)**

## Установка

1. Скачай APK из [Releases](https://github.com/SkeepyKi/dns-switcher-public/releases) и установи на устройство
3. Выдай разрешения через ADB:

```bash
adb shell pm grant com.example.dns_switcher android.permission.WRITE_SECURE_SETTINGS
adb shell appops set com.example.dns_switcher android:get_usage_stats allow
```

> 💡 Можно выдать без ПК — в приложении есть встроенная инструкция с Brevent.

4. Выдай доступ к батарее и уведомлениям в самом приложении
5. Выбери DNS-сервер и приложения-исключения
6. Нажми «Запустить мониторинг» 🚀

## Стек

- Kotlin + Jetpack Compose
- Foreground Service + UsageStatsManager
- Material 3, тёмная тема
