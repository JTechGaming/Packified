package me.jtech.packified.client.util;

import me.jtech.packified.Packified;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class IniUtil {
    private static String defaultIni = "[Window][DockSpaceViewport_11111111]\n" +
            "Pos=0,19\n" +
            "Size=1920,1062\n" +
            "Collapsed=0\n" +
            "\n" +
            "[Window][Debug##Default]\n" +
            "Pos=507,210\n" +
            "Size=624,427\n" +
            "Collapsed=0\n" +
            "\n" +
            "[Window][JSON Editor]\n" +
            "Pos=0,734\n" +
            "Size=1920,347\n" +
            "Collapsed=0\n" +
            "DockId=0x00000006,0\n" +
            "\n" +
            "[Window][File Hierarchy]\n" +
            "Pos=1230,19\n" +
            "Size=690,634\n" +
            "Collapsed=0\n" +
            "DockId=0x00000002,0\n" +
            "\n" +
            "[Window][File Editor]\n" +
            "Pos=0,655\n" +
            "Size=1920,426\n" +
            "Collapsed=0\n" +
            "DockId=0x00000007,0\n" +
            "\n" +
            "[Window][Multiplayer]\n" +
            "Pos=0,19\n" +
            "Size=272,634\n" +
            "Collapsed=0\n" +
            "DockId=0x00000004,0\n" +
            "\n" +
            "[Window][Select Pack]\n" +
            "Pos=1230,19\n" +
            "Size=690,634\n" +
            "Collapsed=0\n" +
            "DockId=0x00000002,1\n" +
            "\n" +
            "[Window][Backups]\n" +
            "Pos=594,141\n" +
            "Size=513,488\n" +
            "Collapsed=0\n" +
            "\n" +
            "[Window][Select Folder]\n" +
            "Pos=444,190\n" +
            "Size=628,326\n" +
            "Collapsed=0\n" +
            "\n" +
            "[Window][ModifyFilePopup]\n" +
            "Pos=1250,19\n" +
            "Size=670,702\n" +
            "Collapsed=0\n" +
            "DockId=0x00000002,1\n" +
            "\n" +
            "[Window][ConfirmPopup]\n" +
            "Pos=553,282\n" +
            "Size=311,111\n" +
            "Collapsed=0\n" +
            "\n" +
            "[Table][0x49F8DCEA,3]\n" +
            "RefScale=13\n" +
            "Column 0  Weight=1.0000\n" +
            "Column 1  Width=47\n" +
            "Column 2  Width=126\n" +
            "\n" +
            "[Table][0x5ED6A88E,3]\n" +
            "RefScale=13\n" +
            "Column 0  Width=413\n" +
            "Column 1  Width=82\n" +
            "Column 2  Width=133\n" +
            "\n" +
            "[Table][0x897F5595,3]\n" +
            "RefScale=13\n" +
            "Column 0  Width=105\n" +
            "Column 1  Width=105\n" +
            "Column 2  Width=105\n" +
            "\n" +
            "[Table][0xC179E37C,3]\n" +
            "RefScale=13\n" +
            "Column 0  Width=108 Sort=0v\n" +
            "Column 1  Width=63\n" +
            "Column 2  Width=-1\n" +
            "\n" +
            "[Table][0xDA36A7E0,6]\n" +
            "RefScale=13\n" +
            "Column 0  Width=28 Sort=0v\n" +
            "Column 1  Width=42\n" +
            "Column 2  Width=73\n" +
            "Column 3  Width=68\n" +
            "Column 4  Weight=1.0000\n" +
            "Column 5  Width=-1\n" +
            "\n" +
            "[Table][0xA43C3885,3]\n" +
            "RefScale=13\n" +
            "Column 0  Width=56\n" +
            "Column 1  Width=56\n" +
            "Column 2  Width=56\n" +
            "\n" +
            "[Table][0x8DFA6E86,2]\n" +
            "Column 0  Weight=1.0000\n" +
            "Column 1  Weight=1.0000\n" +
            "\n" +
            "[Table][0xFABAAEF7,2]\n" +
            "Column 0  Weight=1.0000\n" +
            "Column 1  Weight=1.0000\n" +
            "\n" +
            "[Table][0x45A0E60D,7]\n" +
            "RefScale=13\n" +
            "Column 0  Width=49\n" +
            "Column 1  Width=112\n" +
            "Column 2  Width=112\n" +
            "Column 3  Width=112\n" +
            "Column 4  Width=112\n" +
            "Column 5  Width=112\n" +
            "Column 6  Width=112\n" +
            "\n" +
            "[Table][0xE0773582,3]\n" +
            "Column 0  Weight=1.0000\n" +
            "Column 1  Weight=1.0000\n" +
            "Column 2  Weight=1.0000\n" +
            "\n" +
            "[Table][0x9B8E7538,3]\n" +
            "Column 0  Weight=1.0000\n" +
            "Column 1  Weight=1.0000\n" +
            "Column 2  Weight=1.0000\n" +
            "\n" +
            "[Table][0x47600645,3]\n" +
            "RefScale=13\n" +
            "Column 0  Width=63\n" +
            "Column 1  Width=63\n" +
            "Column 2  Weight=1.0000\n" +
            "\n" +
            "[Table][0xDE6957FF,6]\n" +
            "RefScale=13\n" +
            "Column 0  Width=63\n" +
            "Column 1  Width=63\n" +
            "Column 2  Width=-1\n" +
            "Column 3  Weight=1.0000\n" +
            "Column 4  Weight=1.0000\n" +
            "Column 5  Weight=-1.0000\n" +
            "\n" +
            "[Table][0xC9935533,3]\n" +
            "Column 0  Weight=1.0000\n" +
            "Column 1  Weight=1.0000\n" +
            "Column 2  Weight=1.0000\n" +
            "\n" +
            "[Table][0x3AF7FDCB,3]\n" +
            "RefScale=13\n" +
            "Column 0  Width=242\n" +
            "Column 1  Width=129\n" +
            "Column 2  Width=42\n" +
            "\n" +
            "[Table][0xCCC8D98B,2]\n" +
            "RefScale=13\n" +
            "Column 0  Width=112\n" +
            "Column 1  Width=167\n" +
            "\n" +
            "[Docking][Data]\n" +
            "DockSpace         ID=0x8B93E3BD Window=0xA787BDB4 Pos=0,19 Size=1920,1062 Split=Y Selected=0x1F1A625A\n" +
            "  DockNode        ID=0x00000003 Parent=0x8B93E3BD SizeRef=1920,634 Split=Y\n" +
            "    DockNode      ID=0x00000005 Parent=0x00000003 SizeRef=1920,713 Split=X Selected=0x1F1A625A\n" +
            "      DockNode    ID=0x00000001 Parent=0x00000005 SizeRef=1228,683 Split=X Selected=0x1F1A625A\n" +
            "        DockNode  ID=0x00000004 Parent=0x00000001 SizeRef=272,683 Selected=0xC38394F2\n" +
            "        DockNode  ID=0x00000008 Parent=0x00000001 SizeRef=954,683 CentralNode=1 NoTabBar=1 Selected=0x1F1A625A\n" +
            "      DockNode    ID=0x00000002 Parent=0x00000005 SizeRef=690,683 Selected=0x3CA2B3B6\n" +
            "    DockNode      ID=0x00000006 Parent=0x00000003 SizeRef=1920,347 Selected=0x855DBD76\n" +
            "  DockNode        ID=0x00000007 Parent=0x8B93E3BD SizeRef=1920,426 Selected=0xEFF6FD26\n" +
            "\n";

    public static void setupIni() {
        File iniFile = new File(Packified.MOD_ID + ".ini");
        if (!iniFile.exists()) {
            try {
                iniFile.createNewFile();
                Files.write(iniFile.toPath(), defaultIni.getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
