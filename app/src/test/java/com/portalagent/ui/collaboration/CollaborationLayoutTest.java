package com.portalagent.ui.collaboration;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class CollaborationLayoutTest {

    @Test
    public void slaveListHeaderUsesWeightedContentAndCenteredIcon() throws Exception {
        String source = readSource("src/main/res/layout/fragment_collaboration.xml");
        String iconBlock = source.substring(
            source.indexOf("@+id/collaboration_slave_list_icon"),
            source.indexOf("/>", source.indexOf("@+id/collaboration_slave_list_icon")));

        Assert.assertFalse(source.contains("android:layout_width=\"269dp\""));
        Assert.assertTrue(source.contains("android:layout_width=\"0dp\""));
        Assert.assertTrue(source.contains("android:layout_weight=\"1\""));
        Assert.assertTrue(iconBlock.contains("android:layout_gravity=\"center\""));
        Assert.assertFalse(iconBlock.contains("android:layout_marginEnd"));
    }

    private static String readSource(String relativePath) throws Exception {
        File file = resolveProjectFile(relativePath);
        Assert.assertTrue("Missing source file: " + file.getAbsolutePath(), file.isFile());
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static File resolveProjectFile(String relativePath) {
        File file = new File(relativePath);
        if (!file.isFile()) {
            file = new File("app/" + relativePath);
        }
        return file;
    }
}
