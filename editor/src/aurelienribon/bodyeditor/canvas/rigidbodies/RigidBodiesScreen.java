package aurelienribon.bodyeditor.canvas.rigidbodies;

import aurelienribon.bodyeditor.Ctx;
import aurelienribon.bodyeditor.RigidBodiesEvents;
import aurelienribon.bodyeditor.RigidBodiesManager;
import aurelienribon.bodyeditor.Settings;
import aurelienribon.bodyeditor.canvas.Assets;
import aurelienribon.bodyeditor.canvas.Canvas;
import aurelienribon.bodyeditor.canvas.Label;
import aurelienribon.bodyeditor.canvas.Label.Anchor;
import aurelienribon.bodyeditor.canvas.rigidbodies.input.CreationInputProcessor;
import aurelienribon.bodyeditor.canvas.rigidbodies.input.EditionInputProcessor;
import aurelienribon.bodyeditor.canvas.rigidbodies.input.TestInputProcessor;
import aurelienribon.bodyeditor.models.CircleModel;
import aurelienribon.bodyeditor.models.PolygonModel;
import aurelienribon.bodyeditor.models.RigidBodyModel;
import aurelienribon.bodyeditor.models.ShapeModel;
import aurelienribon.utils.gdx.PolygonUtils;
import aurelienribon.utils.gdx.TextureUtils;
import aurelienribon.utils.notifications.ChangeListener;
import aurelienribon.utils.notifications.ObservableList;
import aurelienribon.utils.notifications.ObservableList.ListChangeListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.BodyDef.BodyType;
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import javax.swing.SwingUtilities;

/**
 *
 * @author Aurelien Ribon | http://www.aurelienribon.com/
 */
public class RigidBodiesScreen {
	private static final float BG_HEIGHT = 25;
	private static final Color BG_LBL_COLOR = new Color(0x2A/255f, 0x3B/255f, 0x56/255f, 180/255f);
	private static final Color BG_BTN_COLOR = new Color(0x2A/255f, 0x6B/255f, 0x56/255f, 180/255f);
	private static enum Mode {CREATION, EDITION, TEST}

	private final Canvas canvas;
	private final RigidBodiesScreenDrawer rbsDrawer;
	private final Box2DDebugRenderer rdr = new Box2DDebugRenderer();

	private final List<Sprite> ballsSprites = new ArrayList<Sprite>();
	private final List<Body> ballsBodies = new ArrayList<Body>();
	private final World world = new World(new Vector2(0, 0), true);

	private final Sprite bgInfo = new Sprite(Assets.inst().get("res/data/white.png", Texture.class));
	private final Label lblModeCreation = new Label(Anchor.TOP_LEFT, 10+BG_HEIGHT, 80, BG_HEIGHT, "Creation", BG_LBL_COLOR);
	private final Label lblModeEdition = new Label(Anchor.TOP_LEFT, 10+BG_HEIGHT*2, 80, BG_HEIGHT, "Edition", BG_LBL_COLOR);
	private final Label lblModeTest = new Label(Anchor.TOP_LEFT, 10+BG_HEIGHT*3, 80, BG_HEIGHT, "Test", BG_LBL_COLOR);
	private final Label lblSetImage = new Label(Anchor.TOP_RIGHT, 10+BG_HEIGHT, 120, BG_HEIGHT, "Set bg. image", BG_BTN_COLOR);
	private final Label lblClearImage = new Label(Anchor.TOP_RIGHT, 15+BG_HEIGHT*2, 120, BG_HEIGHT, "Clear bg. image", BG_BTN_COLOR);
	private final Label lblClearVertices = new Label(Anchor.TOP_RIGHT, 20+BG_HEIGHT*4, 120, BG_HEIGHT, "Clear all points", BG_BTN_COLOR);
	private final Label lblInsertVertices = new Label(Anchor.TOP_RIGHT, 25+BG_HEIGHT*5, 120, BG_HEIGHT, "Insert points", BG_BTN_COLOR);
	private final Label lblRemoveVertices = new Label(Anchor.TOP_RIGHT, 30+BG_HEIGHT*6, 120, BG_HEIGHT, "Remove points", BG_BTN_COLOR);

	private Mode mode = Mode.CREATION;
	private Sprite bodySprite;

	public final ObservableList<Vector2> selectedPoints = new ObservableList<Vector2>();
	public Vector2 nextPoint;
	public Vector2 nearestPoint;
	public Vector2 mouseSelectionP1;
	public Vector2 mouseSelectionP2;
	public Vector2 ballThrowP1;
	public Vector2 ballThrowP2;

	public RigidBodiesScreen(Canvas canvas) {
		this.canvas = canvas;
		this.rbsDrawer = new RigidBodiesScreenDrawer(this, canvas.worldCamera);

		creationInputProcessor = new CreationInputProcessor(canvas, this);
		editionInputProcessor = new EditionInputProcessor(canvas, this);
		testInputProcessor = new TestInputProcessor(canvas, this);
		canvas.input.addProcessor(modeInputProcessor);
		canvas.input.addProcessor(buttonsInputProcessor);
		canvas.input.addProcessor(creationInputProcessor);

		setupBgSprite(bgInfo, 0, 0, 120, 60, BG_LBL_COLOR);

		initializeEvents();
		initializeLabels();
	}

	private void initializeEvents() {
		Ctx.bodies.addChangeListener(new ChangeListener() {
			@Override public void propertyChanged(Object source, String propertyName) {
				if (propertyName.equals(RigidBodiesManager.PROP_SELECTION)) {
					RigidBodyModel model = Ctx.bodies.getSelectedModel();
					setMode(model != null ? Mode.CREATION : null);
					updateButtons();
					resetWorld();
				}
			}
		});

		Ctx.bodiesEvents.addAppToScreenListener(new RigidBodiesEvents.AppToScreenListener() {
			@Override public void recreateWorld() {
				clearWorld();
				createBody();
			}

			@Override public void modelImageChanged() {
				createBodySprite();
				lblClearImage.show();
			}
		});

		selectedPoints.addListChangedListener(new ListChangeListener<Vector2>() {
			@Override public void changed(Object source, List<Vector2> added, List<Vector2> removed) {
				updateButtons();
			}
		});
	}

	private void initializeLabels() {
		Label.TouchCallback modeLblCallback = new Label.TouchCallback() {
			@Override public void touchDown(Label source) {
				setNextMode();
				lblModeCreation.tiltOff();
				lblModeEdition.tiltOff();
				lblModeTest.tiltOff();
			}
			@Override public void touchEnter(Label source) {
				lblModeCreation.tiltOn();
				lblModeEdition.tiltOn();
				lblModeTest.tiltOn();
			}
			@Override public void touchExit(Label source) {
				lblModeCreation.tiltOff();
				lblModeEdition.tiltOff();
				lblModeTest.tiltOff();
			}
		};

		lblModeCreation.setCallback(modeLblCallback);
		lblModeEdition.setCallback(modeLblCallback);
		lblModeTest.setCallback(modeLblCallback);

		lblSetImage.setCallback(new Label.TouchCallback() {
			@Override public void touchEnter(Label source) {}
			@Override public void touchExit(Label source) {}
			@Override public void touchDown(Label source) {
				SwingUtilities.invokeLater(new Runnable() {
					@Override public void run() {Ctx.bodiesEvents.selectModelImage();}
				});
			}
		});

		lblClearImage.setCallback(new Label.TouchCallback() {
			@Override public void touchEnter(Label source) {}
			@Override public void touchExit(Label source) {}
			@Override public void touchDown(Label source) {
				RigidBodyModel model = Ctx.bodies.getSelectedModel();
				if (model != null) {
					model.setImagePath(null);
					createBodySprite();
					lblClearImage.hide();
				}
			}
		});

		lblInsertVertices.setCallback(new Label.TouchCallback() {
			@Override public void touchEnter(Label source) {}
			@Override public void touchExit(Label source) {}
			@Override public void touchDown(Label source) {insertPointsBetweenSelected();}
		});

		lblRemoveVertices.setCallback(new Label.TouchCallback() {
			@Override public void touchEnter(Label source) {}
			@Override public void touchExit(Label source) {}
			@Override public void touchDown(Label source) {removeSelectedPoints();}
		});

		lblClearVertices.setCallback(new Label.TouchCallback() {
			@Override public void touchEnter(Label source) {}
			@Override public void touchExit(Label source) {}
			@Override public void touchDown(Label source) {clearPoints();}
		});
	}

	// -------------------------------------------------------------------------
	// Render
	// -------------------------------------------------------------------------

	public void render() {
		world.step(Gdx.graphics.getDeltaTime(), 10, 10);

		canvas.batch.setProjectionMatrix(canvas.worldCamera.combined);
		canvas.batch.begin();
		if (bodySprite != null && Settings.isImageDrawn) bodySprite.draw(canvas.batch);
		for (int i=0; i<ballsSprites.size(); i++) {
			Sprite sp = ballsSprites.get(i);
			Vector2 pos = ballsBodies.get(i).getPosition();
			float angle = ballsBodies.get(i).getAngle() * MathUtils.radiansToDegrees;
			sp.setPosition(pos.x - sp.getWidth()/2, pos.y - sp.getHeight()/2);
			sp.setRotation(angle);
			sp.draw(canvas.batch);
		}
		canvas.batch.end();

		rbsDrawer.draw(bodySprite);

		canvas.batch.setProjectionMatrix(canvas.screenCamera.combined);
		canvas.batch.begin();
		bgInfo.draw(canvas.batch);
		lblModeCreation.draw(canvas.batch, canvas.font);
		lblModeEdition.draw(canvas.batch, canvas.font);
		lblModeTest.draw(canvas.batch, canvas.font);
		lblSetImage.draw(canvas.batch, canvas.font);
		lblClearImage.draw(canvas.batch, canvas.font);
		lblInsertVertices.draw(canvas.batch, canvas.font);
		lblRemoveVertices.draw(canvas.batch, canvas.font);
		lblClearVertices.draw(canvas.batch, canvas.font);
		canvas.font.setColor(Color.WHITE);
		canvas.font.draw(canvas.batch, String.format(Locale.US, "Zoom: %.0f %%", 100f / canvas.worldCamera.zoom), 10, 45);
		canvas.font.draw(canvas.batch, "Fps: " + Gdx.graphics.getFramesPerSecond(), 10, 25);
		canvas.batch.end();

		if (Settings.isPhysicsDebugEnabled) rdr.render(world, canvas.worldCamera.combined);
	}

	// -------------------------------------------------------------------------
	// Public API
	// -------------------------------------------------------------------------

	public void buildBody() {
		clearWorld();
		createBody();
	}

	public void fireBall(Vector2 orig, Vector2 force) {
		createBall(orig, force);
	}

	public void insertPointsBetweenSelected() {
		if (!isInsertEnabled()) return;

		List<Vector2> toAdd = new ArrayList<Vector2>();

		for (ShapeModel shape : Ctx.bodies.getSelectedModel().getShapes()) {
			if (shape.getType() != ShapeModel.Type.POLYGON) continue;

			List<Vector2> vs = shape.getVertices();

			for (int i=0; i<vs.size(); i++) {
				Vector2 p1 = vs.get(i);
				Vector2 p2 = i != vs.size()-1 ? vs.get(i+1) : vs.get(0);

				if (selectedPoints.contains(p1) && selectedPoints.contains(p2)) {
					Vector2 p = new Vector2((p1.x + p2.x) / 2, (p1.y + p2.y) / 2);
					vs.add(i+1, p);
					toAdd.add(p);
				}
			}
		}

		selectedPoints.addAll(toAdd);
		Ctx.bodies.getSelectedModel().computePhysics();
		Ctx.bodiesEvents.recreateWorld();
	}

	public void removeSelectedPoints() {
		if (!isRemoveEnabled()) return;

		List<ShapeModel> shapes = Ctx.bodies.getSelectedModel().getShapes();

		for (int i=shapes.size()-1; i>=0; i--) {
			ShapeModel shape = Ctx.bodies.getSelectedModel().getShapes().get(i);

			switch (shape.getType()) {
				case POLYGON:
					for (Vector2 p : selectedPoints) {
						if (shape.getVertices().contains(p)) shape.getVertices().remove(p);
					}
					if (shape.getVertices().isEmpty()) shapes.remove(i);
					break;

				case CIRCLE:
					for (Vector2 p : selectedPoints) {
						if (shape.getVertices().contains(p)) {
							shapes.remove(i);
							break;
						}
					}
					break;
			}
		}

		selectedPoints.clear();
		Ctx.bodies.getSelectedModel().computePhysics();
		Ctx.bodiesEvents.recreateWorld();
	}

	// -------------------------------------------------------------------------
	// Internals
	// -------------------------------------------------------------------------

	private void setupBgSprite(Sprite sp, float x, float y, float w, float h, Color c) {
		sp.setPosition(x, y);
		sp.setSize(w, h);
		sp.setColor(c);
	}

	private void updateButtons() {
		RigidBodyModel model = Ctx.bodies.getSelectedModel();

		if (model != null) {
			lblSetImage.show();
			if (model.getImagePath() != null) lblClearImage.show(); else lblClearImage.hide();
			lblClearVertices.show();
			if (isRemoveEnabled()) lblRemoveVertices.show(); else lblRemoveVertices.hide();
			if (isInsertEnabled()) lblInsertVertices.show(); else lblInsertVertices.hide();

		} else {
			lblSetImage.hide();
			lblClearImage.hide();
			lblInsertVertices.hide();
			lblRemoveVertices.hide();
			lblClearVertices.hide();
		}
	}

	private void setMode(Mode mode) {
		this.mode = mode;

		canvas.input.removeProcessor(creationInputProcessor);
		canvas.input.removeProcessor(editionInputProcessor);
		canvas.input.removeProcessor(testInputProcessor);

		selectedPoints.clear();
		nextPoint = null;
		nearestPoint = null;

		if (mode == null) {
			lblModeCreation.hide();
			lblModeEdition.hide();
			lblModeTest.hide();

		} else {
			lblModeCreation.hideSemi();
			lblModeEdition.hideSemi();
			lblModeTest.hideSemi();

			switch (mode) {
				case CREATION:
					lblModeCreation.show();
					canvas.input.addProcessor(creationInputProcessor);
					break;

				case EDITION:
					lblModeEdition.show();
					canvas.input.addProcessor(editionInputProcessor);
					break;

				case TEST:
					lblModeTest.show();
					canvas.input.addProcessor(testInputProcessor);
					break;
			}
		}
	}

	private void setNextMode() {
		Mode m = mode == Mode.CREATION
			? Mode.EDITION : mode == Mode.EDITION
			? Mode.TEST : Mode.CREATION;
		setMode(m);
	}

	private boolean isInsertEnabled() {
		RigidBodyModel model = Ctx.bodies.getSelectedModel();

		if (model == null) return false;
		if (selectedPoints.size() <= 1) return false;

		for (ShapeModel shape : model.getShapes()) {
			if (shape.getType() != ShapeModel.Type.POLYGON) continue;

			Vector2 v1 = null;
			for (Vector2 v2 : shape.getVertices()) {
				if (v1 != null && selectedPoints.contains(v2)) return true;
				v1 = selectedPoints.contains(v2) ? v2 : null;
			}
			if (v1 != null && selectedPoints.contains(shape.getVertices().get(0))) return true;
		}

		return false;
	}

	private boolean isRemoveEnabled() {
		if (Ctx.bodies.getSelectedModel() == null) return false;
		return !selectedPoints.isEmpty();
	}

	private void clearPoints() {
		if (Ctx.bodies.getSelectedModel() == null) return;
		selectedPoints.clear();
		Ctx.bodies.getSelectedModel().clear();
		Ctx.bodiesEvents.recreateWorld();
	}

	private void clearWorld() {
		ballsBodies.clear();
		ballsSprites.clear();
		Iterator<Body> bodies = world.getBodies();
		while (bodies.hasNext()) world.destroyBody(bodies.next());
	}

	private void createBody() {
		RigidBodyModel model = Ctx.bodies.getSelectedModel();
		if (model == null) return;
		if (model.getPolygons().isEmpty() && model.getCircles().isEmpty()) return;

		BodyDef bd = new BodyDef();
		bd.type = BodyType.StaticBody;

		Body body = world.createBody(bd);

		for (PolygonModel polygon : model.getPolygons()) {
			Vector2[] vs = polygon.vertices.toArray(new Vector2[0]);

			if (PolygonUtils.getPolygonArea(vs) < 0.00001f) continue;

			PolygonShape shape = new PolygonShape();
			shape.set(vs);

			FixtureDef fd = new FixtureDef();
			fd.density = 1f;
			fd.friction = 0.5f;
			fd.restitution = 1f;
			fd.shape = shape;

			body.createFixture(fd);
			shape.dispose();
		}

		for (CircleModel circle : model.getCircles()) {
			CircleShape shape = new CircleShape();
			shape.setPosition(circle.center);
			shape.setRadius(circle.radius);

			FixtureDef fd = new FixtureDef();
			fd.density = 1f;
			fd.friction = 0.5f;
			fd.restitution = 1f;
			fd.shape = shape;

			body.createFixture(fd);
			shape.dispose();
		}
	}

	private void createBodySprite() {
		bodySprite = null;

		RigidBodyModel model = Ctx.bodies.getSelectedModel();
		if (model == null) return;

		if (model.isImagePathValid()) {
			File texFile = Ctx.io.getImageFile(model.getImagePath());
			if (texFile == null) return;
			TextureRegion tex = TextureUtils.getPOTTexture(texFile.getPath());
			bodySprite = new Sprite(tex);
		} else {
			bodySprite = new Sprite(Assets.inst().get("res/data/unknown.png", Texture.class));
		}

		bodySprite.setPosition(0, 0);
		bodySprite.setColor(1, 1, 1, 0.5f);

		float spRatio = bodySprite.getWidth() / bodySprite.getHeight();
		if (spRatio >= 1) bodySprite.setSize(1, 1/spRatio);
		else bodySprite.setSize(spRatio, 1);
	}

	private void createBall(Vector2 orig, Vector2 force) {
		Random rand = new Random();
		float radius = rand.nextFloat() * 0.02f + 0.02f;

		BodyDef bd = new BodyDef();
		bd.type = BodyType.DynamicBody;
		bd.angularDamping = 0.5f;
		bd.linearDamping = 0.5f;
		bd.position.set(orig);
		bd.angle = rand.nextFloat() * MathUtils.PI;

		Body b = world.createBody(bd);
		b.applyLinearImpulse(force, orig);

		ballsBodies.add(b);

		CircleShape shape = new CircleShape();
		shape.setRadius(radius);

		FixtureDef fd = new FixtureDef();
		fd.density = 1f;
		fd.friction = 0.5f;
		fd.restitution = 1f;
		fd.shape = shape;

		b.createFixture(fd);

		Sprite sp = new Sprite(Assets.inst().get("res/data/ball.png", Texture.class));
		sp.setSize(radius*2, radius*2);
		sp.setOrigin(sp.getWidth()/2, sp.getHeight()/2);
		ballsSprites.add(sp);
	}

	private void resetWorld() {
		bodySprite = null;
		clearWorld();

		RigidBodyModel model = Ctx.bodies.getSelectedModel();
		if (model == null) return;

		createBody();
		createBodySprite();
	}

	// -------------------------------------------------------------------------
	// Inputs
	// -------------------------------------------------------------------------

	private final InputProcessor creationInputProcessor;
	private final InputProcessor editionInputProcessor;
	private final InputProcessor testInputProcessor;

	private final InputProcessor modeInputProcessor = new InputAdapter() {
		@Override
		public boolean keyDown(int keycode) {
			if (Ctx.bodies.getSelectedModel() != null) {
				if (keycode == Input.Keys.TAB) setNextMode();
			}

			return false;
		}
	};

	private final InputProcessor buttonsInputProcessor = new InputAdapter() {
		@Override
		public boolean touchDown(int x, int y, int pointer, int button) {
			y = Gdx.graphics.getHeight() - y - 1;
			if (lblModeCreation.touchDown(x, y)) return true;
			if (lblModeEdition.touchDown(x, y)) return true;
			if (lblModeTest.touchDown(x, y)) return true;
			if (lblSetImage.touchDown(x, y)) return true;
			if (lblClearImage.touchDown(x, y)) return true;
			if (lblInsertVertices.touchDown(x, y)) return true;
			if (lblRemoveVertices.touchDown(x, y)) return true;
			if (lblClearVertices.touchDown(x, y)) return true;
			return false;
		}

		@Override
		public boolean touchMoved(int x, int y) {
			y = Gdx.graphics.getHeight() - y - 1;
			lblModeCreation.touchMoved(x, y);
			lblModeEdition.touchMoved(x, y);
			lblModeTest.touchMoved(x, y);
			lblSetImage.touchMoved(x, y);
			lblClearImage.touchMoved(x, y);
			lblInsertVertices.touchMoved(x, y);
			lblRemoveVertices.touchMoved(x, y);
			lblClearVertices.touchMoved(x, y);
			return false;
		}
	};
}
