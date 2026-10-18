# YtClient (unofficial InnerTube, Kotlin + Compose)

Open in Android Studio (Koala+) -> Sync -> Run. (Gradle wrapper jar nahi hai; Android Studio khud handle karta hai.)

Flow:
1. Login: WebView Google login -> cookies save (Session.kt) -> SAPISIDHASH auth (Auth.kt)
2. Home: /browse FEwhat_to_watch (personalized feed), infinite scroll via continuation
3. Play: ANDROID_VR client se direct streams (no ads), Media3 merge video+audio
4. Algorithm: WEB client se playbackTracking URLs + cpn, start ping + har 30s watchtime ping + final ping
   => watch history/recommendations update hote hain.

Known issues / TODO:
- Streams throttle ho sakte hain (range chunking add karo ya NewPipeExtractor use karo).
- ANDROID_VR client age-restricted / kuch videos pe fail hota hai -> fallback clients (IOS, TVHTML5) add karo.
- WEB_VERSION / client versions purane hone par update karo.
- YouTube ToS ke against; secondary account se test karo.
