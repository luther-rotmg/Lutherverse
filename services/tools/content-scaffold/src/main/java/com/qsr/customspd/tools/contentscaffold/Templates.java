package com.qsr.customspd.tools.contentscaffold;

/** Java source templates for scaffolded content. The generated code compiles and runs;
 *  the mechanic and real art are left as TODOs. */
public final class Templates {
    private Templates() {}

    private static final String HEADER =
            "/*\n * Lutherverse -- scaffolded content stub.\n"
          + " * GPLv3; see the project license. Replace the TODOs with the real mechanic + art.\n */\n";

    public static String mobClass(Names n) {
        return HEADER
          + "package com.qsr.customspd.actors.mobs;\n\n"
          + "import com.qsr.customspd.actors.Char;\n"
          + "import com.qsr.customspd.sprites." + n.className() + "Sprite;\n"
          + "import com.watabou.utils.Random;\n\n"
          + "public class " + n.className() + " extends Mob {\n\n"
          + "\t{\n"
          + "\t\tspriteClass = " + n.className() + "Sprite.class;\n\n"
          + "\t\t// TODO: real stats\n"
          + "\t\tHP = HT = 10;\n"
          + "\t\tdefenseSkill = 5;\n"
          + "\t\tmaxLvl = 10;\n"
          + "\t}\n\n"
          + "\t@Override\n"
          + "\tpublic int damageRoll() {\n"
          + "\t\t// TODO: mechanic\n"
          + "\t\treturn Random.NormalIntRange(1, 4);\n"
          + "\t}\n\n"
          + "\t@Override\n"
          + "\tpublic int attackSkill(Char target) {\n"
          + "\t\treturn 10;\n"
          + "\t}\n\n"
          + "\t@Override\n"
          + "\tpublic int drRoll() {\n"
          + "\t\treturn super.drRoll();\n"
          + "\t}\n"
          + "}\n";
    }

    public static String mobSprite(Names n) {
        String a = "GeneralAsset." + n.upperSnake();
        return HEADER
          + "package com.qsr.customspd.sprites;\n\n"
          + "import com.qsr.customspd.assets.Asset;\n"
          + "import com.qsr.customspd.assets.GeneralAsset;\n"
          + "import com.watabou.noosa.TextureFilm;\n\n"
          + "public class " + n.className() + "Sprite extends MobSprite {\n"
          + "\tpublic " + n.className() + "Sprite() {\n"
          + "\t\tsuper();\n"
          + "\t\ttexture(Asset.getAssetFilePath(" + a + "));\n"
          + "\t\t// TODO: real frames + art. The placeholder is a single 16x16 frame.\n"
          + "\t\tTextureFilm frames = new TextureFilm(texture, 16, 16);\n"
          + "\t\tidle = new Animation(1, true);\n"
          + "\t\tidle.frames(frames, 0);\n"
          + "\t\trun = new Animation(1, true);\n"
          + "\t\trun.frames(frames, 0);\n"
          + "\t\tattack = new Animation(1, false);\n"
          + "\t\tattack.frames(frames, 0);\n"
          + "\t\tdie = new Animation(1, false);\n"
          + "\t\tdie.frames(frames, 0);\n"
          + "\t\tplay(idle);\n"
          + "\t}\n"
          + "}\n";
    }

    public static String itemClass(Names n) {
        return HEADER
          + "package com.qsr.customspd.items;\n\n"
          + "import com.qsr.customspd.assets.GeneralAsset;\n\n"
          + "public class " + n.className() + " extends Item {\n\n"
          + "\t{\n"
          + "\t\timage = GeneralAsset." + n.upperSnake() + ";\n"
          + "\t\t// TODO: stackable / defaultAction / mechanic\n"
          + "\t}\n\n"
          + "\t@Override\n"
          + "\tpublic boolean isUpgradable() {\n"
          + "\t\treturn false;\n"
          + "\t}\n\n"
          + "\t@Override\n"
          + "\tpublic boolean isIdentified() {\n"
          + "\t\treturn true;\n"
          + "\t}\n"
          + "}\n";
    }

    public static String generalAssetLine(Names n, boolean mob) {
        String path = mob ? n.mobAssetPath() : n.itemAssetPath();
        return "    " + n.upperSnake() + "(\"" + path + "\"),";
    }

    public static String messageLines(Names n, boolean mob) {
        String base = mob ? ("actors.mobs." + n.lower()) : ("items." + n.lower());
        return base + ".name=" + n.className().toLowerCase() + "\n"
             + base + ".desc=TODO: describe this " + (mob ? "creature" : "item") + ".\n";
    }
}
