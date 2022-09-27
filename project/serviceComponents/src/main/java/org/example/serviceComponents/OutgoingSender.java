package org.example.serviceComponents;

import org.example.PacketOut;

/**
 * inderection. required (for example) by Videomodule to request packet lacks
 */
public interface OutgoingSender {
    void send(PacketOut packet);
}
