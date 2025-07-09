package jMonkeyEngine.Tests;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.plugins.ZipLocator;
import com.jme3.bounding.BoundingBox;
import com.jme3.font.BitmapText;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;

/** Sample 3 - how to load an OBJ model, and OgreXML model,
 * a material/texture, or text. */
public class HelloAssets extends SimpleApplication {

    public static void main(String[] args) {
        HelloAssets app = new HelloAssets();
        app.start();
    }

    @Override
    public void simpleInitApp() {
        /* Load a teapot model (OBJ file from jme3-testdata) */
        Spatial teapot = assetManager.loadModel("Models/Teapot.obj");
        teapot.scale(0.05f);
        Material mat_default = new Material( assetManager, "Common/MatDefs/Light/Lighting.j3md");
        teapot.setMaterial(mat_default);
        rootNode.attachChild(teapot);

        /* Create a wall (Box with material and texture from jme3-testdata) */
        Box box = new Box(2.5f,2.5f,1.0f);
        Spatial wall = new Geometry("Box", box );
        Material mat_brick = new Material( assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        //mat_brick.setTexture("ColorMap", assetManager.loadTexture("Textures/Terrain/BrickWall/BrickWall.jpg"));
        wall.setMaterial(mat_brick);
        wall.setLocalTranslation(2.0f,-2.5f,0.0f);
        rootNode.attachChild(wall);

        /* Display a line of text (default font from jme3-testdata) */
        setDisplayStatView(false);
        guiFont = assetManager.loadFont("Interface/Fonts/Default.fnt");
        BitmapText helloText = new BitmapText(guiFont);
        helloText.setSize(guiFont.getCharSet().getRenderedSize());
        helloText.setText("Hello World");
        helloText.setLocalTranslation(300, helloText.getLineHeight(), 0);
        guiNode.attachChild(helloText);

        /* Load a Ninja model (OgreXML + material + texture from test_data) */
        Spatial ninja = assetManager.loadModel("Models/Ninja.obj");

        // Center ninja at origin (adjust Y by bounding box center)
        ninja.setLocalTranslation(0f, -5f, -2f);
        ninja.setLocalScale(0.075f);
        ninja.setCullHint(Spatial.CullHint.Never);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", ColorRGBA.Green);
        ninja.setMaterial(mat);

        rootNode.attachChild(ninja);

        // Lighting
        DirectionalLight sun = new DirectionalLight();
        sun.setDirection(new Vector3f(-0.5f, -1f, -1f).normalizeLocal());
        sun.setColor(ColorRGBA.White);
        rootNode.addLight(sun);

        AmbientLight ambient = new AmbientLight();
        ambient.setColor(ColorRGBA.White.mult(0.2f)); // softer than sun
        rootNode.addLight(ambient);


        // Ground
        Box groundBox = new Box(50, 0f, 50);
        Geometry ground = new Geometry("Ground", groundBox);
        Material groundMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        groundMat.setColor("Color", ColorRGBA.Gray);
        ground.setMaterial(groundMat);
        ground.setLocalTranslation(0, -5f, 0);
        rootNode.attachChild(ground);

        Spatial gameLevel = assetManager.loadModel("Scenes/town/main.scene");
        gameLevel.setLocalTranslation(0, -5.2f, 0);
        gameLevel.setLocalScale(2);
        rootNode.attachChild(gameLevel);
    }
}
