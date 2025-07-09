package jMonkeyEngine.Tests;

import com.jme3.app.SimpleApplication;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Box;

/** Sample 4 - how to trigger repeating actions from the main event loop.
 * In this example, you use the loop to make the player character
 * rotate continuously. */
public class HelloLoop extends SimpleApplication {

    public static void main(String[] args){
        HelloLoop app = new HelloLoop();
        app.start();
    }

    private Geometry player;
    private Geometry player2;

    @Override
    public void simpleInitApp() {
        /** this blue box is our player character */
        Box b = new Box(1, 1, 1);
        player = new Geometry("blue cube", b);
        player2 = new Geometry("red cube", b);
        Material mat = new Material(assetManager,
                                    "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", ColorRGBA.Blue);
        player.setMaterial(mat);

        Material mat2 = new Material(assetManager,
                                    "Common/MatDefs/Misc/Unshaded.j3md");
        mat2.setColor("Color", ColorRGBA.Red);
        player2.setMaterial(mat2);

        player.setLocalTranslation(new Vector3f(1, -1, 1));
        player2.setLocalTranslation(new Vector3f(5,-1,1));

        rootNode.attachChild(player);
        rootNode.attachChild(player2);
    }

    /* Use the main event loop to trigger repeating actions. */
    @Override
    public void simpleUpdate(float tpf) {
        // make the player rotate:
        player2.rotate(0, 2*tpf, 0);
        player.rotate(0, 0, 2*tpf);
        player.move(-2*tpf, 0, 0);
    }
}
