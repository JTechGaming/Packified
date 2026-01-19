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
    Pos=0,29
    Size=3840,2130
    Collapsed=0

            [Window][JSON Editor]
    Pos=0,734
    Size=1920,347
    Collapsed=0
    DockId=0x00000006,0

            [Window][File Hierarchy]
    Pos=2939,30
    Size=891,1440
    Collapsed=0
    DockId=0x00000014,0

            [Window][File Editor]
    Pos=4,1472
    Size=3826,678
    Collapsed=0
    DockId=0x0000000B,0

            [Window][Multiplayer]
    Pos=4,30
    Size=512,1279
    Collapsed=0
    DockId=0x00000019,0

            [Window][Select Pack]
    Pos=1477,491
    Size=430,545
    Collapsed=0

            [Window][Backups]
    Pos=626,183
    Size=373,370
    Collapsed=0

            [Window][Select Folder]
    Pos=1296,262
    Size=883,299
    Collapsed=0

            [Window][ModifyFilePopup]
    Pos=1276,300
    Size=631,87
    Collapsed=0

            [Window][ConfirmPopup]
    Pos=1371,602
    Size=642,322
    Collapsed=0

            [Window][Pixel Art Editor]
    Pos=870,655
    Size=1050,425
    Collapsed=0
    DockId=0x00000007,0

            [Window][Settings]
    Pos=1064,470
    Size=1256,587
    Collapsed=0

            [Window][Text Editor Color Settings]
    Pos=854,243
    Size=481,323
    Collapsed=0

            [Window][Pack Creation Wizard]
    Pos=1338,547
    Size=710,432
    Collapsed=0

            [Window][Logs]
    Pos=4,1472
    Size=3826,678
    Collapsed=0
    DockId=0x0000000B,1

            [Window][File Hierarchy (2/4)##1]
    Pos=606,266
    Size=400,130
    Collapsed=0

            [Window][Asset Inspector]
    Pos=299,42
    Size=951,533
    Collapsed=0

            [Window][Start (1/6)##1]
    Pos=606,266
    Size=400,130
    Collapsed=0

            [Window][Pack (2/6)##1]
    Pos=606,266
    Size=400,130
    Collapsed=0

            [Window][File Hierarchy (3/6)##1]
    Pos=606,266
    Size=400,130
    Collapsed=0

            [Window][File Editor (3/4)##1]
    Pos=606,266
    Size=400,130
    Collapsed=0

            [Window][Model Editor]
    Pos=438,30
    Size=2586,1440
    Collapsed=0
    DockId=0x00000013,1

            [Window][Model Elements]
    Pos=3026,1205
    Size=804,945
    Collapsed=0
    DockId=0x00000002,0

            [Window][Element Properties]
    Pos=3026,30
    Size=804,1173
    Collapsed=0
    DockId=0x00000001,0

            [Window][Textures]
    Pos=4,1170
    Size=432,980
    Collapsed=0
    DockId=0x0000000A,0

            [Window][UV Editor]
    Pos=4,30
    Size=432,1138
    Collapsed=0
    DockId=0x00000008,0

            [Window][DockHost]
    Pos=0,0
    Size=3840,2161
    Collapsed=0

            [Window][File Explorer##0]
    Pos=437,28
    Size=2587,1493
    Collapsed=0
    DockId=0x00000013,2

            [Window][Main]
    Collapsed=0

            [Window][Editor]
    Collapsed=0
    DockId=0x14E60461

            [Window][Log]
    Collapsed=0
    DockId=0x14E60461

            [Window][Backup]
    Collapsed=0
    DockId=0x14E60461

            [Window][Pack Browser]
    Collapsed=0
    DockId=0x14E60461

            [Window][Model Picker]
    Pos=1186,513
    Size=1015,500
    Collapsed=0

            [Window][Version Control]
    Pos=4,30
    Size=512,1279
    Collapsed=0
    DockId=0x00000019,1

            [Window][World]
    Pos=4,1311
    Size=512,159
    Collapsed=0
    DockId=0x0000001A,0

            [Table][0x49F8DCEA,3]
    RefScale=13
    Column 0  Weight=1.0000
    Column 1  Width=47
    Column 2  Width=126

            [Table][0x5ED6A88E,3]
    RefScale=20.5714
    Column 0  Width=545
    Column 1  Width=123
    Column 2  Width=209

            [Table][0x897F5595,3]
    RefScale=13
    Column 0  Width=105
    Column 1  Width=105
    Column 2  Width=105

            [Table][0xC179E37C,3]
    RefScale=13
    Column 0  Width=108 Sort=0v
    Column 1  Width=63
    Column 2  Width=-1

            [Table][0xDA36A7E0,6]
    RefScale=13
    Column 0  Width=28 Sort=0v
    Column 1  Width=42
    Column 2  Width=73
    Column 3  Width=68
    Column 4  Weight=1.0000
    Column 5  Width=-1

            [Table][0xA43C3885,3]
    RefScale=13
    Column 0  Width=56
    Column 1  Width=56
    Column 2  Width=56

            [Table][0x8DFA6E86,2]
    Column 0  Weight=1.0000
    Column 1  Weight=1.0000

            [Table][0xFABAAEF7,2]
    Column 0  Weight=1.0000
    Column 1  Weight=1.0000

            [Table][0x45A0E60D,7]
    RefScale=13
    Column 0  Width=49
    Column 1  Width=112
    Column 2  Width=112
    Column 3  Width=112
    Column 4  Width=112
    Column 5  Width=112
    Column 6  Width=112

            [Table][0xE0773582,3]
    Column 0  Weight=1.0000
    Column 1  Weight=1.0000
    Column 2  Weight=1.0000

            [Table][0x9B8E7538,3]
    Column 0  Weight=1.0000
    Column 1  Weight=1.0000
    Column 2  Weight=1.0000

            [Table][0x47600645,3]
    RefScale=13
    Column 0  Width=63
    Column 1  Width=63
    Column 2  Weight=1.0000

            [Table][0xDE6957FF,6]
    RefScale=13
    Column 0  Width=63
    Column 1  Width=63
    Column 2  Width=-1
    Column 3  Weight=1.0000
    Column 4  Weight=1.0000
    Column 5  Weight=-1.0000

            [Table][0xC9935533,3]
    Column 0  Weight=1.0000
    Column 1  Weight=1.0000
    Column 2  Weight=1.0000

            [Table][0x3AF7FDCB,3]
    RefScale=13
    Column 0  Width=242
    Column 1  Width=129
    Column 2  Width=42

            [Table][0xCCC8D98B,2]
    RefScale=13
    Column 0  Width=104
    Column 1  Width=172

            [Table][0x938E2BB8,3]
    RefScale=13
    Column 0  Width=443
    Column 1  Width=44
    Column 2  Width=94

            [Table][0xE60FEE26,2]
    RefScale=21.7143
    Column 0  Width=149
    Column 1  Width=236

            [Docking][Data]
    DockSpace               ID=0x14E60461 Pos=0,0 Size=3840,2161 CentralNode=1
    DockSpace               ID=0x1FF6FA18 Window=0x9BD87705 Pos=4,30 Size=3826,2120 Split=X Selected=0x1F1A625A
    DockNode              ID=0x0000000D Parent=0x1FF6FA18 SizeRef=2543,1345 Split=X Selected=0x1F1A625A
    DockNode            ID=0x00000004 Parent=0x0000000D SizeRef=432,1345 Split=Y Selected=0xF8A78665
    DockNode          ID=0x00000008 Parent=0x00000004 SizeRef=475,788 Selected=0xF8A78665
    DockNode          ID=0x0000000A Parent=0x00000004 SizeRef=475,678 Selected=0x5CF1DB40
    DockNode            ID=0x00000009 Parent=0x0000000D SizeRef=2586,1345 Split=X Selected=0x1F1A625A
    DockNode          ID=0x00000015 Parent=0x00000009 SizeRef=1278,1438 Split=Y Selected=0x1F1A625A
    DockNode        ID=0x00000017 Parent=0x00000015 SizeRef=2479,719 Split=Y Selected=0x1F1A625A
    DockNode      ID=0x0000000C Parent=0x00000017 SizeRef=3826,1440 Split=X Selected=0x1F1A625A
    DockNode    ID=0x00000011 Parent=0x0000000C SizeRef=512,1537 Split=Y Selected=0xC38394F2
    DockNode  ID=0x00000019 Parent=0x00000011 SizeRef=512,1279 Selected=0xC38394F2
    DockNode  ID=0x0000001A Parent=0x00000011 SizeRef=512,159 Selected=0xFBB63E47
    DockNode    ID=0x00000012 Parent=0x0000000C SizeRef=3312,1537 Split=X Selected=0x1F1A625A
    DockNode  ID=0x00000013 Parent=0x00000012 SizeRef=2419,1537 CentralNode=1 Selected=0x1F1A625A
    DockNode  ID=0x00000014 Parent=0x00000012 SizeRef=891,1537 Selected=0x3CA2B3B6
    DockNode      ID=0x0000000B Parent=0x00000017 SizeRef=3826,678 Selected=0xEFF6FD26
    DockNode        ID=0x00000018 Parent=0x00000015 SizeRef=2479,717 Selected=0x10BDF14E
    DockNode          ID=0x00000016 Parent=0x00000009 SizeRef=1218,1438 Selected=0x12A83ED1
    DockNode              ID=0x0000000E Parent=0x1FF6FA18 SizeRef=804,1345 Split=Y Selected=0x5416DE7C
    DockNode            ID=0x00000001 Parent=0x0000000E SizeRef=804,812 Selected=0x5416DE7C
    DockNode            ID=0x00000002 Parent=0x0000000E SizeRef=804,654 Selected=0x95984034
    DockSpace               ID=0x8B93E3BD Pos=0,29 Size=3840,2130 Split=Y Selected=0x1F1A625A
    DockNode              ID=0x00000003 Parent=0x8B93E3BD SizeRef=1920,615 Split=Y
    DockNode            ID=0x00000005 Parent=0x00000003 SizeRef=1920,713 Split=Y Selected=0x1F1A625A
    DockNode          ID=0x0000000F Parent=0x00000005 SizeRef=2681,32 Selected=0x65E32145
    DockNode          ID=0x00000010 Parent=0x00000005 SizeRef=2681,1392 CentralNode=1 NoTabBar=1 Selected=0x1F1A625A
    DockNode            ID=0x00000006 Parent=0x00000003 SizeRef=1920,347 Selected=0x855DBD76
    DockNode              ID=0x00000007 Parent=0x8B93E3BD SizeRef=1920,439 Selected=0xEFF6FD26""";

    public static void setupIni() {
        File iniFile = new File(Packified.MOD_ID + ".ini");
        if (!iniFile.exists()) {
            try {
                iniFile.createNewFile();
                Files.write(iniFile.toPath(), defaultIni.getBytes());
            } catch (IOException e) {
                LogWindow.addError("Failed to create default ini file: " + e.getMessage());
            }
        }
    }
}