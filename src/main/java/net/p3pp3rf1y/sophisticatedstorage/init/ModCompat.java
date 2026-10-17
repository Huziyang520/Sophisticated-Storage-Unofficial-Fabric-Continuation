package net.p3pp3rf1y.sophisticatedstorage.init;

/*import net.p3pp3rf1y.sophisticatedstorage.compat.mkb.ModernKeyBindingCompat;
import net.p3pp3rf1y.sophisticatedstorage.compat.sodium.SodiumCompat;*/

// 26.3 移植：chipped / litematica / sb / tomsstorage 兼容实现依赖已删除的 API，暂放于 src/parked/java，
// 相应注册在此移除；恢复时把实现移回 src/main 并加回注册。
public class ModCompat {
	private ModCompat() {
	}

	public static final String MKB = "mkb";

	public static void register() {
		//CompatRegistry.registerCompat(new CompatInfo(CompatModIds.QUARK, null), () -> new QuarkCompat());
		/*CompatRegistry.registerCompat(new CompatInfo(SODIUM, CompatRegistry.fromSpec(">=0.4.9 <0.5")), () -> new SodiumCompat());
		CompatRegistry.registerCompat(new CompatInfo(MKB, null), () -> new ModernKeyBindingCompat());*/
	}
}
