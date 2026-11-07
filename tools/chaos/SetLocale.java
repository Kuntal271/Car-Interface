import android.content.res.Configuration;
import android.os.LocaleList;
import java.util.Locale;

public class SetLocale {
    public static void main(String[] a) throws Exception {
        Object am = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
        Configuration c = (Configuration) am.getClass().getMethod("getConfiguration").invoke(am);
        c.setLocales(new LocaleList(Locale.forLanguageTag(a[0])));
        Configuration.class.getField("userSetLocale").setBoolean(c, true);
        am.getClass().getMethod("updatePersistentConfiguration", Configuration.class).invoke(am, c);
        System.out.println("locale -> " + a[0]);
    }
}
