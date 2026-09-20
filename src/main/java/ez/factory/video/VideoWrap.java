package ez.factory.video;

import ez.factory.config.Config;
import ez.factory.util.Exec;
import ez.factory.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Static-video wrap (brand spec §5): every finished poster becomes a silent MP4 —
 * subtle Ken Burns push-in, fade in/out, 8-12 s, 1080x1920, H.264 yuv420p.
 * The SAME mp4 goes to all six channels.
 */
public final class VideoWrap {

    private static final String TAG = "video";

    public static File wrap(Config cfg, File posterPng, File outMp4) throws IOException {
        int frames = cfg.videoSeconds * cfg.videoFps;
        double fadeOutStart = cfg.videoSeconds - 0.6;
        // 1.1x overscan gives the Ken Burns push room without edge artifacts
        String vf = "scale=" + (int) (cfg.canvasW * 1.1) + ":" + (int) (cfg.canvasH * 1.1)
                + ",zoompan=z='min(zoom+0.00045,1.10)':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)'"
                + ":d=" + frames + ":s=" + cfg.canvasW + "x" + cfg.canvasH + ":fps=" + cfg.videoFps
                + ",fade=t=in:st=0:d=0.6,fade=t=out:st=" + fadeOutStart + ":d=0.6,format=yuv420p";
        List<String> cmd = new ArrayList<>();
        cmd.add(cfg.ffmpegPath);
        cmd.add("-y");
        cmd.add("-loop"); cmd.add("1");
        cmd.add("-i"); cmd.add(posterPng.getAbsolutePath());
        cmd.add("-vf"); cmd.add(vf);
        cmd.add("-t"); cmd.add(String.valueOf(cfg.videoSeconds));
        cmd.add("-c:v"); cmd.add("libx264");
        cmd.add("-preset"); cmd.add("medium");
        cmd.add("-crf"); cmd.add("20");
        cmd.add("-an");
        cmd.add("-movflags"); cmd.add("+faststart");
        cmd.add(outMp4.getAbsolutePath());
        Exec.Result r = Exec.run(cmd, 600);
        if (!r.ok()) throw new IOException("ffmpeg failed (exit " + r.exit + "): " + tail(r.out));
        Log.info(TAG, "static video: " + outMp4.getName() + " (" + cfg.videoSeconds + "s)");
        return outMp4;
    }

    /** ffprobe-free check via ffmpeg -i (duration + resolution). */
    public static List<String> probe(Config cfg, File mp4) {
        List<String> problems = new ArrayList<>();
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(cfg.ffmpegPath); cmd.add("-i"); cmd.add(mp4.getAbsolutePath());
            Exec.Result r = Exec.run(cmd, 60);
            String out = r.out;
            if (!out.contains(cfg.canvasW + "x" + cfg.canvasH)) problems.add("mp4 resolution is not " + cfg.canvasW + "x" + cfg.canvasH);
            int k = out.indexOf("Duration: ");
            if (k >= 0) {
                String t = out.substring(k + 10, k + 18);
                try {
                    String[] p = t.split(":");
                    double sec = Integer.parseInt(p[0]) * 3600 + Integer.parseInt(p[1]) * 60 + Double.parseDouble(p[2]);
                    if (sec < 7.5 || sec > 12.5) problems.add("mp4 duration " + sec + "s outside 8-12s");
                } catch (Exception ignore) { }
            }
        } catch (IOException e) {
            problems.add("cannot probe mp4: " + e.getMessage());
        }
        return problems;
    }

    private static String tail(String s) {
        return s.length() > 600 ? s.substring(s.length() - 600) : s;
    }
}
