package org.embeddedt.embeddium.impl.gui.rso.compat;

/** USB HID scancodes from SDL's public API mapped to GLFW's public key constants. */
public final class SdlKeyMapping {
    private SdlKeyMapping() {}

    public static int glfwKey(int scan) {
        if (scan >= 4 && scan <= 29) return 65 + scan - 4;
        if (scan >= 30 && scan <= 38) return 49 + scan - 30;
        if (scan >= 58 && scan <= 69) return 290 + scan - 58;
        if (scan >= 89 && scan <= 97) return 321 + scan - 89;
        if (scan >= 104 && scan <= 115) return 302 + scan - 104;
        return switch (scan) {
            case 39 -> 48;
            case 40 -> 257;
            case 41 -> 256;
            case 42 -> 259;
            case 43 -> 258;
            case 44 -> 32;
            case 45 -> 45;
            case 46 -> 61;
            case 47 -> 91;
            case 48 -> 93;
            case 49 -> 92;
            case 50 -> 161;
            case 51 -> 59;
            case 52 -> 39;
            case 53 -> 96;
            case 54 -> 44;
            case 55 -> 46;
            case 56 -> 47;
            case 57 -> 280;
            case 70 -> 283;
            case 71 -> 281;
            case 72 -> 284;
            case 73 -> 260;
            case 74 -> 268;
            case 75 -> 266;
            case 76 -> 261;
            case 77 -> 269;
            case 78 -> 267;
            case 79 -> 262;
            case 80 -> 263;
            case 81 -> 264;
            case 82 -> 265;
            case 83 -> 282;
            case 84 -> 331;
            case 85 -> 332;
            case 86 -> 333;
            case 87 -> 334;
            case 88 -> 335;
            case 98 -> 320;
            case 99 -> 330;
            case 100 -> 162;
            case 101 -> 348;
            case 103 -> 336;
            case 224 -> 341;
            case 225 -> 340;
            case 226 -> 342;
            case 227 -> 343;
            case 228 -> 345;
            case 229 -> 344;
            case 230 -> 346;
            case 231 -> 347;
            default -> -1;
        };
    }
}
