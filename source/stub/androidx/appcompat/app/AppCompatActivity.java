package androidx.appcompat.app;

/*
 * Compile-only facade for javac.
 * Binary superclass name is identical to the real androidx.appcompat.app.AppCompatActivity,
 * so the emitted .class links against the real class at runtime (it exists in the APK's dex).
 */
public class AppCompatActivity extends android.app.Activity {
}
