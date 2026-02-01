package me.jtech.packified.client.util;

import me.jtech.packified.Packified;
import me.jtech.packified.client.windows.LogWindow;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@Environment(EnvType.CLIENT)
public class IniUtil {
    private static String defaultIni = """
            [Window][DockSpaceViewport_11111111]
            Pos=4,1311
            Size=512,159
            Collapsed=0
            DockId=0x0000001A,0
            
            [Window][DockHost]
            Pos=0,0
            Size=3840,2161
            Collapsed=0
            
            [Window][Debug##Default]
            Pos=60,60
            Size=400,400
            Collapsed=0
            
            [Window][Logs]
            Pos=4,1428
            Size=3826,722
            Collapsed=0
            DockId=0x00000006,0
            
            [Window][File Editor]
            Pos=4,1428
            Size=3826,722
            Collapsed=0
            DockId=0x00000006,2
            
            [Window][File Hierarchy]
            Pos=2955,30
            Size=875,1396
            Collapsed=0
            DockId=0x00000002,0
            
            [Window][Version Control]
            Pos=4,30
            Size=533,1248
            Collapsed=0
            DockId=0x00000007,1
            
            [Window][World]
            Pos=4,1280
            Size=533,146
            Collapsed=0
            DockId=0x00000008,0
            
            [Window][Multiplayer]
            Pos=4,30
            Size=533,1248
            Collapsed=0
            DockId=0x00000007,0
            
            [Window][File Explorer 1]
            Pos=4,1428
            Size=3826,722
            Collapsed=0
            DockId=0x00000006,1
            
            [Window][Settings]
            Pos=1449,842
            Size=942,475
            Collapsed=0
            
            [Window][Pack Exporter]
            Pos=1394,803
            Size=1052,554
            Collapsed=0
            
            [Window][Select Pack]
            Pos=1771,950
            Size=298,259
            Collapsed=0
            
            [Window][Backups]
            Pos=2953,30
            Size=877,1396
            Collapsed=0
            DockId=0x00000002,0
            
            [Window][Model Editor]
            Pos=522,30
            Size=2656,1396
            Collapsed=0
            DockId=0x0000000D,1
            
            [Window][Model Elements]
            Pos=3180,1050
            Size=650,376
            Collapsed=0
            DockId=0x00000010,0
            
            [Window][Element Properties]
            Pos=3180,30
            Size=650,1018
            Collapsed=0
            DockId=0x0000000F,0
            
            [Window][Textures]
            Pos=4,940
            Size=516,486
            Collapsed=0
            DockId=0x0000000C,0
            
            [Window][UV Editor]
            Pos=4,30
            Size=516,908
            Collapsed=0
            DockId=0x0000000B,0
            
            [Window][Pack Creation Wizard]
            Pos=1356,820
            Size=1128,519
            Collapsed=0
            
            [Table][0xE60FEE26,2]
            RefScale=21.7143
            Column 0  Width=150
            Column 1  Width=110
            
            [Table][0x5ED6A88E,3]
            RefScale=21.7143
            Column 0  Width=499
            Column 1  Width=111
            Column 2  Width=166
            
            [Docking][Data]
            DockSpace             ID=0x1FF6FA18 Window=0x9BD87705 Pos=4,30 Size=3826,2120 Split=Y Selected=0x1F1A625A
              DockNode            ID=0x00000005 Parent=0x1FF6FA18 SizeRef=3826,1396 Split=X
                DockNode          ID=0x00000003 Parent=0x00000005 SizeRef=533,2120 Split=Y Selected=0x231A4D21
                  DockNode        ID=0x00000007 Parent=0x00000003 SizeRef=533,1248 Selected=0xC38394F2
                  DockNode        ID=0x00000008 Parent=0x00000003 SizeRef=533,146 Selected=0xFBB63E47
                DockNode          ID=0x00000004 Parent=0x00000005 SizeRef=3291,2120 Split=X
                  DockNode        ID=0x00000001 Parent=0x00000004 SizeRef=2414,2120 Split=X Selected=0x1F1A625A
                    DockNode      ID=0x00000009 Parent=0x00000001 SizeRef=516,1396 Split=Y Selected=0xF8A78665
                      DockNode    ID=0x0000000B Parent=0x00000009 SizeRef=901,908 Selected=0xF8A78665
                      DockNode    ID=0x0000000C Parent=0x00000009 SizeRef=901,486 Selected=0x5CF1DB40
                    DockNode      ID=0x0000000A Parent=0x00000001 SizeRef=3308,1396 Split=X Selected=0x1F1A625A
                      DockNode    ID=0x0000000D Parent=0x0000000A SizeRef=2656,1396 CentralNode=1 Selected=0x1F1A625A
                      DockNode    ID=0x0000000E Parent=0x0000000A SizeRef=650,1396 Split=Y Selected=0x5416DE7C
                        DockNode  ID=0x0000000F Parent=0x0000000E SizeRef=650,1018 Selected=0x5416DE7C
                        DockNode  ID=0x00000010 Parent=0x0000000E SizeRef=650,376 Selected=0x95984034
                  DockNode        ID=0x00000002 Parent=0x00000004 SizeRef=875,2120 Selected=0x3CA2B3B6
              DockNode            ID=0x00000006 Parent=0x1FF6FA18 SizeRef=3826,722 Selected=0xEFF6FD26""";

    public static void setupIni() {
        File iniFile = new File(Packified.MOD_ID + ".ini");
        boolean exists = iniFile.exists();
        boolean outdated = false;
        if (exists) {
            try {
                outdated = !Files.readString(iniFile.toPath()).contains("Pack Exporter");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        if (!exists || outdated) {
            try {
                if (!exists)
                    iniFile.createNewFile();
                Files.write(iniFile.toPath(), defaultIni.getBytes());
            } catch (IOException e) {
                LogWindow.addError("Failed to create default ini file: " + e.getMessage());
            }
        }
    }
}