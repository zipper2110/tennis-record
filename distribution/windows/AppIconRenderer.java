import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.attributes.ViewBox;
import com.github.weisj.jsvg.parser.SVGLoader;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders the app icon SVG into a multi-size Windows ICO file.
 * Usage: java -cp jsvg.jar AppIconRenderer.java <svg> <ico> [png-directory]
 */
public class AppIconRenderer {
    private static final int[] ICO_SIZES = {16, 20, 24, 32, 40, 48, 64, 128, 256};
    private static final int[] PNG_SIZES = {32, 64, 128, 256, 512};

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: AppIconRenderer <svg> <ico> [png-directory]");
        }
        SVGDocument document = new SVGLoader().load(Path.of(args[0]).toUri().toURL());
        if (document == null) {
            throw new IOException("Cannot parse SVG: " + args[0]);
        }

        List<byte[]> images = new ArrayList<>();
        for (int size : ICO_SIZES) {
            images.add(png(render(document, size)));
        }
        writeIco(Path.of(args[1]), images);

        if (args.length > 2) {
            Path pngDirectory = Path.of(args[2]);
            Files.createDirectories(pngDirectory);
            for (int size : PNG_SIZES) {
                Files.write(pngDirectory.resolve("tennis-record-" + size + ".png"), png(render(document, size)));
            }
        }
    }

    private static BufferedImage render(SVGDocument document, int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            document.render(null, graphics, new ViewBox(0, 0, size, size));
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }

    private static void writeIco(Path output, List<byte[]> images) throws IOException {
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream stream = Files.newOutputStream(output)) {
            LittleEndian out = new LittleEndian(stream);
            out.u16(0);
            out.u16(1);
            out.u16(images.size());
            int offset = 6 + 16 * images.size();
            for (int i = 0; i < images.size(); i++) {
                int size = ICO_SIZES[i];
                out.u8(size >= 256 ? 0 : size);
                out.u8(size >= 256 ? 0 : size);
                out.u8(0);
                out.u8(0);
                out.u16(1);
                out.u16(32);
                out.u32(images.get(i).length);
                out.u32(offset);
                offset += images.get(i).length;
            }
            for (byte[] image : images) {
                stream.write(image);
            }
        }
    }

    private record LittleEndian(OutputStream stream) {
        void u8(int value) throws IOException {
            stream.write(value & 0xFF);
        }

        void u16(int value) throws IOException {
            u8(value);
            u8(value >>> 8);
        }

        void u32(int value) throws IOException {
            u16(value);
            u16(value >>> 16);
        }
    }
}
