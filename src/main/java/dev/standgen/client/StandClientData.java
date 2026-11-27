package dev.standgen.client;

import dev.standgen.stand.Archetype;
import dev.standgen.stand.StandData;
import dev.standgen.stand.StandForm;

/** Копія даних стенда на клієнті (приходить із сервера) для меню стенда. */
public record StandClientData(String name, int primary, int secondary, int form, int stage, int milestones,
                              int power, int speed, int range, int durability, int precision, int mastery,
                              float[] scores, boolean canEdit, int element, int animal, int variant) {
    public static volatile StandClientData current;

    /** Легка копія StandData: потрібна, щоб порахувати здібності за тими самими правилами, що й на сервері. */
    public StandData asData() {
        StandData d = new StandData();
        d.primary = Archetype.values()[Math.max(0, Math.min(primary, Archetype.COUNT - 1))];
        d.secondary = Archetype.values()[Math.max(0, Math.min(secondary, Archetype.COUNT - 1))];
        d.form = StandForm.values()[Math.max(0, Math.min(form, StandForm.values().length - 1))];
        d.element = dev.standgen.stand.Element.byId(element);
        d.animal = dev.standgen.stand.Animal.byId(animal);
        d.variant = variant;
        d.formId = Math.max(0, Math.min(form, StandForm.values().length - 1));
        d.stage = stage;
        d.milestones = milestones;
        return d;
    }
}
