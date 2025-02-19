package me.jtech.packified.packets;

import me.jtech.packified.Packified;
import net.minecraft.util.Identifier;

public class NetworkingConstants {
    /**
        * C2S
     */
    public static final Identifier C2S_SYNC_PACK_CHANGES = Identifier.of(Packified.MOD_ID, "c2s_sync_pack");
    public static final Identifier C2S_SEND_FULL_PACK = Identifier.of(Packified.MOD_ID, "c2s_send_pack");
    public static final Identifier C2S_REQUEST_FULL_PACK = Identifier.of(Packified.MOD_ID, "c2s_request_pack");
    public static final Identifier C2S_HAS_MOD = Identifier.of(Packified.MOD_ID, "c2s_has_mod");

    /**
     * S2C
     */
    public static final Identifier S2C_SYNC_PACK_CHANGES = Identifier.of(Packified.MOD_ID, "s2c_sync_pack");
    public static final Identifier S2C_SEND_FULL_PACK = Identifier.of(Packified.MOD_ID, "s2c_send_pack");
    public static final Identifier S2C_REQUEST_FULL_PACK = Identifier.of(Packified.MOD_ID, "s2c_request_pack");
    public static final Identifier S2C_PLAYER_HAS_MOD = Identifier.of(Packified.MOD_ID, "s2c_player_has_mod");
}
