package me.andromedov.paradisepaints.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ColorPickerModelTest {
    @Test void convertsRgbAndHsvWithoutDrift() {
        for(int rgb:new int[]{0xff0000,0x00ff00,0x0000ff,0xabcdef,0x000000,0xffffff}) {
            var picker=new ColorPickerModel(rgb);
            assertEquals(rgb,picker.rgb());
        }
    }
    @Test void clampsPointerValues() {
        var picker=new ColorPickerModel(0);
        picker.setHue(2); picker.setSaturationValue(-1,2);
        assertEquals(1,picker.hue()); assertEquals(0,picker.saturation()); assertEquals(1,picker.value());
        assertEquals(0xffffff,picker.rgb());
    }
}
