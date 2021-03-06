package com.spaceproject.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.spaceproject.components.BoundsComponent;
import com.spaceproject.components.CameraFocusComponent;
import com.spaceproject.components.CannonComponent;
import com.spaceproject.components.PlanetComponent;
import com.spaceproject.components.TextureComponent;
import com.spaceproject.components.TransformComponent;
import com.spaceproject.components.VehicleComponent;
import com.spaceproject.config.LandConfig;
import com.spaceproject.generation.EntityFactory;
import com.spaceproject.screens.SpaceScreen;
import com.spaceproject.screens.WorldScreen;
import com.spaceproject.utility.Mappers;
import com.spaceproject.utility.MyMath;
import com.spaceproject.utility.MyScreenAdapter;

public class PlayerControlSystem extends EntitySystem {

	private Engine engine;
	
	public boolean inSpace;	
	LandConfig landCFG;
	
	//target reference
	private Entity playerEntity; //the player entity

	//vehicles array to check if player can get in 
	private ImmutableArray<Entity> vehicles;
	private ImmutableArray<Entity> planets;

	//action timer, for enter/exit vehicle
	//TODO: move to component, both player and AI need to be able to enter/exit
	private float timeSinceVehicle;
	private int timeTillCanGetInVehicle;
	
	//CONTROLS///////////////////////////////////////////////
	//movement
	public static boolean moveForward; 
	public static boolean moveLeft;
	public static boolean moveRight;
	public static boolean applyBreaks;
	public static boolean stop;//if vehicle should stop instantly-debug stop
	
	public static float movementMultiplier;	//for analog control. will be value between 1 and 0
	public static float angleFacing;//set direction player faces
	
	//actions
	//player should shoot	
	public static boolean shoot;
	//player should enter/exit vehicle	
	public static boolean changeVehicle;
	//landing on planets
	public static boolean land;
	public static boolean canLand;
	private static boolean animateLanding;
	private static boolean animationFinished;
	//END Contols//////////////////////////////////////	
	
	
	public PlayerControlSystem(ScreenAdapter screen, Entity player, LandConfig landConfig) {
		//this.screen = screen;
		inSpace = (screen instanceof SpaceScreen);
		
		this.playerEntity = player;	
		this.landCFG = landConfig;
		
		timeSinceVehicle = 0;
		timeTillCanGetInVehicle = 60;

		//movement
		moveForward = false; 
		moveLeft = false;
		moveRight = false;
		applyBreaks = false;
		stop = false;
		
		movementMultiplier = 0;
		angleFacing = 0;
		
		//actions
		shoot = false;
		changeVehicle = false;
		land = false;
		canLand = false;
		animateLanding = false;
		animationFinished = false;
	}
	
	/*
	public PlayerControlSystem(ScreenAdapter screen, Entity player, Entity vehicle, LandConfig landConfig) {
		this(screen, player, landConfig);
		this.vehicleEntity = vehicle;
	}*/


	@Override
	public void addedToEngine(Engine engine) {
		this.engine = engine;	
		
		//playerEntity = engine.getEntitiesFor(Family.one(PlayerFocusComponent.class).get()).first();
		vehicles = engine.getEntitiesFor(Family.all(VehicleComponent.class).get());
		planets = engine.getEntitiesFor(Family.all(PlanetComponent.class).get());
	}


	@Override
	public void update(float delta) {
	
		//update getting in/out of vehicle action timer
		if (timeSinceVehicle < timeTillCanGetInVehicle) {
			timeSinceVehicle += 100 * delta;
		}
		
		Entity vehicleEntity = Mappers.character.get(playerEntity).vehicle;
		 
		if (isInVehicle(playerEntity)) {			
			controlShip(vehicleEntity, delta);
		} else {	
			controlCharacter(playerEntity, delta);
		}
		
		landAndTakeoff(delta);
	}

	private void landAndTakeoff(float delta) {
		if (inSpace) {
			canLand = canLandOnPlanet();		
			if (animateLanding) {
				animatePlanetLanding(delta);
			}
			if (animationFinished) {
				MyScreenAdapter.changeScreen(new WorldScreen(landCFG));
			}
		} else {
			//TODO: Create some kind of system so planets orbital position based is on time
			// because when the player leaves a planet the orbit is reset.
			//TODO: only true if player is in ship on planet
			//canTakeOff = inVehicle
			canLand = true;//true for debug
			if (land) { MyScreenAdapter.changeScreen(new SpaceScreen(landCFG)); }
			
			if (animateLanding) {
				//animateSpaceTakeOff(delta);
			}
			if (animationFinished) {
				//MyScreenAdapter.changeScreen(new SpaceScreen(landCFG));
			}
		}
		if (land && canLand) {
			animateLanding = true;
		}
	}

	/**
	 * Animate landing on planet. First shrink texture, then zoom camera.
	 * @param delta
	 */
	private void animatePlanetLanding(float delta) {
		if (!animateLanding || animationFinished) {
			return;
		}
		
		Entity player = engine.getEntitiesFor(Family.one(CameraFocusComponent.class).get()).first();
		
		//freeze position
		player.getComponent(TransformComponent.class).velocity.set(0, 0); 
		
		TextureComponent tex = player.getComponent(TextureComponent.class);
		
		//shrink texture
		tex.scale -= 3f * delta; 
		if (tex.scale <= 0.1f) {
			tex.scale = 0;
			//TODO: Finish animation. fade screen white/blurry/some effect
			
			//zoom in
			MyScreenAdapter.setZoomTarget(0);
			if (MyScreenAdapter.cam.zoom <= 0.1f) {
				animationFinished = true;
			}			
		}
	}
	
	private boolean canLandOnPlanet() {
		if (!isInVehicle(playerEntity)) {
			return false;
		}
		Entity vehicleEntity = Mappers.character.get(playerEntity).vehicle;
		Vector3 playerPos = Mappers.transform.get(vehicleEntity).pos;
		for (Entity planet : planets) {
			Vector3 planetPos = Mappers.transform.get(planet).pos;
			TextureComponent planetTex = Mappers.texture.get(planet);
			// if player is over planet
			if (MyMath.distance(playerPos.x, playerPos.y, planetPos.x, planetPos.y) <= planetTex.texture.getWidth() * 0.5 * planetTex.scale) {
				landCFG = new LandConfig();
				landCFG.position = planetPos;// save position for taking off from planet
				landCFG.planet = Mappers.planet.get(planet); // save seed for planet
				landCFG.shipSeed = Mappers.vehicle.get(vehicleEntity).seed; //save seed for ship
				return true;
			}
		}
		return false;
	}

	/**
	 * Control the character.
	 * @param playerEntity 
	 * @param delta
	 */
	private void controlCharacter(Entity playerEntity, float delta) {
		//players position
		TransformComponent transform = Mappers.transform.get(playerEntity);
		
		//make character face mouse/joystick
		transform.rotation = MathUtils.lerpAngle(transform.rotation, angleFacing, 8f*delta);
		
		if (moveForward) {
			float walkSpeed = Mappers.character.get(playerEntity).walkSpeed;
			float dx = (float) Math.cos(transform.rotation) * (walkSpeed * movementMultiplier) * delta;
			float dy = (float) Math.sin(transform.rotation) * (walkSpeed * movementMultiplier) * delta;
			transform.pos.add(dx, dy, 0);
		}
		
		if (changeVehicle) {
			enterVehicle();
		}
	}

	/**
	 * Control the ship.
	 * @param delta
	 */
	private void controlShip(Entity vehicleEntity, float delta) {
		
		TransformComponent transform = Mappers.transform.get(vehicleEntity);
		VehicleComponent vehicle = Mappers.vehicle.get(vehicleEntity);
		
		CannonComponent cannon = Mappers.cannon.get(vehicleEntity);	
		refillAmmo(cannon, delta);
		
		//make vehicle face angle from mouse/joystick
		transform.rotation = MathUtils.lerpAngle(transform.rotation, angleFacing, 8f*delta);
		
		
		//apply thrust forward accelerate 
		if (moveForward) {
			accelerate(delta, transform, vehicle);
		}
		
		//apply thrust left
		if (moveLeft) {
			accelLeft(delta, transform, vehicle);
		}
		
		//apply thrust right
		if (moveRight) {
			accelRight(delta, transform, vehicle);
		}
		
		//stop vehicle
		if (applyBreaks) {
			decelerate(delta, transform);
		}
		
		//fire cannon / attack
		if (shoot) {
			fireCannon(transform, cannon, Mappers.vehicle.get(vehicleEntity).id);
		}
		
		//debug force insta-stop
		if (stop) {
			transform.velocity.set(0,0);
			stop = false;
		}
		
		//exit vehicle
		if (changeVehicle) {
			exitVehicle(vehicleEntity);
		}
			
	}

	/**
	 * Slow down ship. When ship is slow enough, ship will stop completely
	 * @param delta
	 * @param transform
	 */
	private static void decelerate(float delta, TransformComponent transform) {
		int stopThreshold = 20; 
		int minBreakingThrust = 10;
		int maxBreakingThrust = 1500;
		if (transform.velocity.len() <= stopThreshold) {
			//completely stop if moving really slowly
			transform.velocity.set(0,0);
		} else {
			//add thrust opposite direction of velocity to slow down ship
			float thrust = MathUtils.clamp(transform.velocity.len(), minBreakingThrust, maxBreakingThrust);
			float angle = transform.velocity.angle();
			float dx = (float) Math.cos(angle) * thrust * delta;
			float dy = (float) Math.sin(angle) * thrust * delta;
			transform.velocity.add(dx, dy);
		}
	}

	/**
	 * Move ship to the right. TODO: change this to dodge mechanic.
	 * @param delta
	 * @param transform
	 * @param movement
	 * @param vehicle
	 */
	private void accelRight(float delta, TransformComponent transform, VehicleComponent vehicle) {
		float thrust = vehicle.thrust * 0.6f;
		float angle = transform.rotation - 1.57f;
		float dx = (float) Math.cos(angle) * (thrust * movementMultiplier) * delta;
		float dy = (float) Math.sin(angle) * (thrust * movementMultiplier) * delta;
		transform.velocity.add(dx, dy);
		if (vehicle.maxSpeed != -1)
			transform.velocity.clamp(0, vehicle.maxSpeed);
	}

	/**
	 * Move ship to the left. TODO: change this to dodge mechanic.
	 * @param delta
	 * @param transform
	 * @param movement
	 * @param vehicle
	 */
	private void accelLeft(float delta, TransformComponent transform, VehicleComponent vehicle) {
		float thrust = vehicle.thrust * 0.6f;
		float angle = transform.rotation + 1.57f;
		float dx = (float) Math.cos(angle) * (thrust * movementMultiplier) * delta;
		float dy = (float) Math.sin(angle) * (thrust * movementMultiplier) * delta;
		transform.velocity.add(dx, dy);
		if (vehicle.maxSpeed != -1)
			transform.velocity.clamp(0, vehicle.maxSpeed);
	}

	/**
	 * Move ship forward.
	 * @param delta
	 * @param transform
	 * @param movement
	 * @param vehicle
	 */
	private void accelerate(float delta, TransformComponent transform, VehicleComponent vehicle) {
		//TODO: create a vector method for the dx = cos... dy = sin... It's used multiple times in the program(movement, missiles..)
		//TODO: implement rest of engine behavior
		//float maxSpeedMultiplier? on android touch controls make maxSpeed be relative to finger distance so that finger distance determines how fast to go			
	
		float thrust = vehicle.thrust;
		float angle = transform.rotation;
		float dx = (float) Math.cos(angle) * (thrust * movementMultiplier) * delta;
		float dy = (float) Math.sin(angle) * (thrust * movementMultiplier) * delta;
		transform.velocity.add(dx, dy);
		
		//cap speed at max. if maxSpeed set to -1 it's infinite(no cap)
		if (vehicle.maxSpeed != -1)
			transform.velocity.clamp(0, vehicle.maxSpeed);
	}

	/**
	 * Refill ammo for the cannon
	 * @param cannon
	 * @param delta 
	 */
	private static void refillAmmo(CannonComponent cannon, float delta) {
		//TODO: cannon refill logic needs to be moved to system, all ships need to recharge
		// deal with cannon timers
		cannon.timeSinceLastShot -= 100 * delta;
		cannon.timeSinceRecharge -= 100 * delta;
		if (cannon.timeSinceRecharge < 0 && cannon.curAmmo < cannon.maxAmmo) {
			//refill ammo
			cannon.curAmmo++;		
			
			//reset timer
			cannon.timeSinceRecharge = cannon.rechargeRate;
		}
	}

	/**
	 * Fire cannon.
	 * @param transform of ship
	 * @param movement of ship
	 * @param cannon
	 */
	private void fireCannon(TransformComponent transform, CannonComponent cannon, long ID) {
		//check if can fire before shooting
		if (!canFire(cannon))
			return;
		
		//reset timer if ammo is full, to prevent instant recharge
		if (cannon.curAmmo == cannon.maxAmmo) {			
			cannon.timeSinceRecharge = cannon.rechargeRate;
		}		
		
		//create missile	
		float dx = (float) (Math.cos(transform.rotation) * cannon.velocity) + transform.velocity.x;
		float dy = (float) (Math.sin(transform.rotation) * cannon.velocity) + transform.velocity.y;
		engine.addEntity(EntityFactory.createMissile(transform, new Vector2(dx, dy), cannon, ID));
		
		//subtract ammo
		--cannon.curAmmo;
		
		//reset timer
		cannon.timeSinceLastShot = cannon.fireRate;
		
		/*
		 * Cheat for debug:
		 * fast firing and infinite ammo
		 */
		boolean cheat = false;
		if (cheat) {
			cannon.curAmmo++;
			cannon.timeSinceLastShot = -1;
		}
	}

	/**
	 * Check if has enough ammo and time past since last shot.
	 * @param cannon
	 * @return true if can fire
	 */
	private static boolean canFire(CannonComponent cannon) {
		return cannon.curAmmo > 0 && cannon.timeSinceLastShot <= 0;
	}
	
	
	/**
	 * Enter nearest vehicle if available.
	 */
	public void enterVehicle() {
		//check if already in vehicle
		if (isInVehicle(playerEntity)) {
			return;
		}
		
		//action timer
		if (timeSinceVehicle < timeTillCanGetInVehicle) {
			return;
		}
		timeSinceVehicle = 0;
		
		
		//get all vehicles and check if player is close to one(bounds overlap)
		BoundsComponent playerBounds = Mappers.bounds.get(playerEntity);
		for (Entity vehicle : vehicles) {
			
			//skip vehicle is occupied
			if (Mappers.vehicle.get(vehicle).driver != null) continue;
			
			//check if character is near a vehicle
			BoundsComponent vehicleBounds = Mappers.bounds.get(vehicle);
			if (playerBounds.poly.getBoundingRectangle().overlaps(vehicleBounds.poly.getBoundingRectangle())) {			
				//if (Intersector.overlapConvexPolygons(vehicleBounds.poly, playerBounds.poly)) {
				
				//set references
				Mappers.character.get(playerEntity).vehicle = vehicle;
				Mappers.vehicle.get(vehicle).driver = playerEntity;
				
				// set focus to vehicle
				playerEntity.remove(CameraFocusComponent.class);
				vehicle.add(new CameraFocusComponent());
				
				// remove player from engine
				engine.removeEntity(playerEntity);
				
				// zoom out camera, TODO: add pan animation
				MyScreenAdapter.setZoomTarget(1);
				
				return;
			}
		}
	}
			
	/**
	 * Exit current vehicle.
	 * @param vehicleEntity 
	 */
	public void exitVehicle(Entity vehicleEntity) {
		//check if not in vehicle
		if (!isInVehicle(playerEntity)) {
			return;
		}
		
		//action timer
		if (timeSinceVehicle < timeTillCanGetInVehicle) {
			return;
		}
		timeSinceVehicle = 0;

		
		// set the player at the position of vehicle
		Vector3 vehiclePosition = Mappers.transform.get(vehicleEntity).pos;
		Mappers.transform.get(playerEntity).pos.set(vehiclePosition);
		
		// set focus to player entity
		vehicleEntity.remove(CameraFocusComponent.class);
		playerEntity.add(new CameraFocusComponent());
	
		// remove references
		Mappers.character.get(playerEntity).vehicle = null;
		Mappers.vehicle.get(vehicleEntity).driver = null;
		
		// add player back into world
		engine.addEntity(playerEntity);
		
		// zoom in camera
		MyScreenAdapter.setZoomTarget(0.5f);
		
	}

	/**
	 * Check if player is in vehicle.
	 * @param vehicleEntity 
	 * @return true if in vehicle
	 */
	public boolean isInVehicle(Entity character) {
		//return vehicleEntity != null;
		return Mappers.character.get(character).vehicle != null;
	}

}
