package ez.factory.schedule;

import ez.factory.config.Config;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Publishing clock (brand spec §6): 2 posts/day at 10:00 and 18:00 Europe/Tirane,
 * one item per run. The daemon fires the pipeline `leadMinutes` before each slot and
 * hands Postiz the exact slot time, so posts go out on the minute even if the PC hiccups.
 */
public final class SlotClock {

    public static ZoneId zone(Config cfg) { return ZoneId.of(cfg.timezone); }

    public static List<LocalTime> times(Config cfg) {
        List<LocalTime> out = new ArrayList<>();
        for (String s : cfg.slotTimes) out.add(LocalTime.parse(s.trim(), DateTimeFormatter.ofPattern("H:mm")));
        return out;
    }

    /** The next slot strictly after `after`. */
    public static ZonedDateTime nextSlot(Config cfg, ZonedDateTime after) {
        ZoneId z = zone(cfg);
        ZonedDateTime t = after.withZoneSameInstant(z);
        for (int day = 0; day < 3; day++) {
            LocalDate d = t.toLocalDate().plusDays(day);
            for (LocalTime lt : times(cfg)) {
                ZonedDateTime cand = ZonedDateTime.of(d, lt, z);
                if (cand.isAfter(t)) return cand;
            }
        }
        throw new IllegalStateException("no slot found");
    }

    /** The slot the daemon should produce next: earliest upcoming slot not yet scheduled. */
    public static ZonedDateTime nextProductionSlot(Config cfg, String lastScheduledIso, ZonedDateTime now, int leadMinutes) {
        ZonedDateTime cand = nextSlot(cfg, now.minusMinutes(1));
        // walk forward until we find a slot after lastScheduledIso whose lead window has opened
        for (int i = 0; i < 14; i++) {
            boolean already = !lastScheduledIso.isEmpty() && !cand.toInstant().toString().equals(lastScheduledIso)
                    && cand.toInstant().isBefore(java.time.Instant.parse(lastScheduledIso));
            boolean leadOpen = !now.isBefore(cand.minusMinutes(leadMinutes));
            if (!already && leadOpen) return cand;
            if (!already && !leadOpen) return cand; // produce as soon as the window opens
            cand = nextSlot(cfg, cand);
        }
        return cand;
    }
}
