import java.io.File;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.IIOImage;
public class CinemaJpeg {
    public static void main(String[] args) throws Exception {
        BufferedImage image = ImageIO.read(new File(args[0]));
        if (image.getWidth()!=2048 || image.getHeight()!=1024) throw new Exception("Invalid panorama dimensions");
        var writer=ImageIO.getImageWritersByFormatName("jpeg").next();
        try(var out=ImageIO.createImageOutputStream(new File(args[1]))) {
            writer.setOutput(out);
            var params=writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);params.setCompressionQuality(.86f);
            writer.write(null,new IIOImage(image,null,null),params);
        } finally {writer.dispose();image.flush();}
    }
}
