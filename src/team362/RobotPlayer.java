package team362;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import battlecode.common.*;

public class RobotPlayer {
	
	// Strategic stages
	private static boolean introMode = true;
	private static boolean transitionMode = false;
	private static boolean turtleMode = false;
	
	private static MapLocation turtleCorner;
//	private final static int PARTS_THRESHOLD = 100;
	private static final int RUBBLE_LOWER_CLEAR_THRESHOLD = 40;
	private static final Direction[] DIRECTIONS = {Direction.NORTH, Direction.NORTH_EAST, Direction.EAST,
			Direction.SOUTH_EAST, Direction.SOUTH, Direction.SOUTH_WEST, Direction.WEST, Direction.NORTH_WEST};
	private static Random rand;
	
	private final static int NUM_INTRO_SCOUTS = 2;
	private static final int ARCHON_RESERVED_DISTANCE_SQUARED = 3;
	private static int fullMapRadius = GameConstants.MAP_MAX_HEIGHT*GameConstants.MAP_MAX_WIDTH;
	private static int numArchons = 0;
	
	private static final int NUM_TURNS_TO_CHECK_FOR_ZOMBIES = 3;
	
	private static final List<MapLocation> ZOMBIE_DEN_LOCATIONS = new ArrayList<>();
	
	private static final List<MapLocation> CORNERS_ALREADY_SCOUTED = new ArrayList<>();
	
	// Commands for signals
	private static final int SENDING_DEN_X = 1;
	private static final int SENDING_DEN_Y = 2;
	private static final int SENDING_TURTLE_X = 3;
	private static final int SENDING_TURTLE_Y = 4;
	private static final int SENDING_TARGET_DIRECTION = 5;
	private static final int SENDING_CORNER_X = 6;
	private static final int SENDING_CORNER_Y = 7;
	private static final int SENDING_PART_LOCATION_X = 8;
	private static final int SENDING_PART_LOCATION_Y = 9;
	private static final int SENDING_PART_LOCATION_VALUE = 10;
	
	private static final int NORTH_EAST_CORNER = 1;
	private static final int NORTH_WEST_CORNER = 2;
	private static final int SOUTH_EAST_CORNER = 3;
	private static final int SOUTH_WEST_CORNER = 4;
	
	
	public static void run(RobotController rc) {
		rand = new Random(rc.getID());
		if (rc.getType() == RobotType.ARCHON) {
			archon(rc);
		} else if (rc.getType() == RobotType.SCOUT) {
			scout(rc);
		} else if (rc.getType() == RobotType.TURRET) {
			turret(rc);
		} else if (rc.getType() == RobotType.TTM) {
			ttm(rc);
		} else if (rc.getType() == RobotType.SOLDIER) {
			soldier(rc);
		} else if (rc.getType() == RobotType.GUARD) {
			guard(rc);
		} else if (rc.getType() == RobotType.VIPER) {
			viper(rc);
		}
	}

	/**
	 * TODO - about archon strategy here
	 * @param rc
	 * @throws GameActionException
	 */
	private static void archon(RobotController rc){
		numArchons = rc.getInitialArchonLocations(rc.getTeam()).length;
		while (true) {
			try {
				if (introMode) {
					processIntroMessageSignals(rc);
					if (CORNERS_ALREADY_SCOUTED.size() >= 2) {
						List<Direction> crossDirections = Arrays.asList(Direction.NORTH_WEST,
								Direction.NORTH_EAST, Direction.SOUTH_WEST, Direction.SOUTH_EAST);
						if (!(crossDirections.contains(CORNERS_ALREADY_SCOUTED.get(0)
								.directionTo(CORNERS_ALREADY_SCOUTED.get(1))) || CORNERS_ALREADY_SCOUTED.size() > 2)) {
							// TODO if they are not opposite, make a scout and send to one of the remaining corners
						} else {
							MapLocation[] newCorners = {new MapLocation(CORNERS_ALREADY_SCOUTED.get(0).x, CORNERS_ALREADY_SCOUTED.get(1).y),
									new MapLocation(CORNERS_ALREADY_SCOUTED.get(1).x, CORNERS_ALREADY_SCOUTED.get(0).y)};
							for (MapLocation corner : newCorners) {
								if(!CORNERS_ALREADY_SCOUTED.contains(corner)) {
									CORNERS_ALREADY_SCOUTED.add(corner);
								}
							}
							introMode = false;
							transitionMode = true;
							turtleCorner = findBestTurtleCorner(rc);
							//TODO determine fullMapRadius
							// (also broadcast this locally to scouts when you make them? So they can message
							// back more efficiently, if they have to)
						}
					}
					
					// TODO gathering parts (need locations from scouts first...)
					if (rc.getRobotCount() - numArchons < NUM_INTRO_SCOUTS) {
						tryToBuild(rc, RobotType.SCOUT);
						Direction directionToTargetCorner = calculateDirectionToClosestCorner(rc);
						rc.broadcastMessageSignal(SENDING_TARGET_DIRECTION, DIRECTION_TO_SIGNAL.get(directionToTargetCorner), 4);
					} else {
						if (rand.nextDouble() > .66666) { // 2:1 soldier:guard ratio
							tryToBuild(rc, RobotType.GUARD);
						} else {
							tryToBuild(rc, RobotType.SOLDIER);
						}
					}
				} else if (transitionMode) {
					//TODO transition
					// have archon broadcast turtleCorner to nearby bots or just have them follow the 
					// archon when it moves? unless there are things to fight
					if (rc.getLocation().distanceSquaredTo(turtleCorner) > 4) {
						moveTowards(rc, rc.getLocation().directionTo(turtleCorner));						
					} else {
						// TODO get in proper position
						transitionMode = false;
						turtleMode = true;
					}
					
					if (rand.nextDouble() > .66666) { // 2:1 soldier:guard ratio
						tryToBuild(rc, RobotType.GUARD);
					} else {
						tryToBuild(rc, RobotType.SOLDIER);
					}
				} else if (turtleMode) {
					tryToBuild(rc, RobotType.SOLDIER);
				}
				repairFirst(rc);
				Clock.yield();				
			} catch (GameActionException e) {
				e.printStackTrace();
			}
		}
	}

	private static int turnsToCheckForZombies = 0;
	private static void scout(RobotController rc) {
		// do one time things here
		Direction targetDirection = Direction.NONE;
		while (true) {
			try {
				if (introMode) {
					if (targetDirection.equals(Direction.NONE)) {
						targetDirection = getScoutTargetDirection(rc);
					}
					
					RobotInfo[] zombies = rc.senseNearbyRobots(-1, Team.ZOMBIE);
					for (RobotInfo zombie : zombies) {
						if (zombie.type == RobotType.ZOMBIEDEN) {
							if (!ZOMBIE_DEN_LOCATIONS.contains(zombie.location)) {
								ZOMBIE_DEN_LOCATIONS.add(zombie.location);
								rc.broadcastMessageSignal(SENDING_DEN_X, zombie.location.x, fullMapRadius);
								rc.broadcastMessageSignal(SENDING_DEN_Y, zombie.location.y, fullMapRadius);
							}
						}
					}
					
					MapLocation corner = checkForCorner(rc, targetDirection);
					if (corner != null) {
						rc.broadcastMessageSignal(SENDING_CORNER_X, corner.x, fullMapRadius);
						rc.broadcastMessageSignal(SENDING_CORNER_Y, corner.y, fullMapRadius);
						// head to opposite in case other scout didn't make it
						// TODO or scout around for other zombie dens, kite, etc.
						targetDirection = targetDirection.opposite();
					}
					moveTowards(rc, targetDirection);
				} else {
					RobotInfo[] zombies = rc.senseNearbyRobots(-1, Team.ZOMBIE);
					if (zombies.length > 0) {
						turnsToCheckForZombies = NUM_TURNS_TO_CHECK_FOR_ZOMBIES;					
					}
					if (turnsToCheckForZombies > 0) {
						turnsToCheckForZombies--;
						leadZombiesToEnemy(rc);
					} else {
						
					}
//				rc.sensePartLocations(-1); //TODO
				}
				Clock.yield();				
			} catch (GameActionException e) {
				e.printStackTrace();
			}
		}
	}

	private static void guard(RobotController rc) {
		// do one time things here
		while (true) {
			try {
				if (introMode) {
					//TODO smarter attacking (make a method to pick and attack, move if appropriate)
					boolean attacked = attackFirst(rc);
					if (!attacked && rc.isCoreReady() && rc.isWeaponReady()) {
						//so that you don't try to move and get delay if you should be getting ready to attack
						moveTowardsNearestEnemy(rc);
					}
				}
				Clock.yield();				
			} catch (GameActionException e) {
				e.printStackTrace();
			}
		}
	}

	private static void soldier(RobotController rc) {
		// do one time things here
		while (true) {
			try {
				if (introMode) {
					//TODO smarter attacking (make a method to pick and attack, move if appropriate)
					boolean attacked = attackFirst(rc);
					if (!attacked && rc.isCoreReady() && rc.isWeaponReady()) {
						//so that you don't try to move and get delay if you should be getting ready to attack
						boolean moved = moveTowardsNearestEnemy(rc);
						if (!moved) {
							moveAwayFromArchons(rc);
						}
					}
				}
				Clock.yield();				
			} catch (GameActionException e) {
				e.printStackTrace();
			}
		}
	}

	private static void turret(RobotController rc) {
		// do one time things here
		while (true) {
//			try {
				Clock.yield();				
//			} catch (GameActionException e) {
//				e.printStackTrace();
//			}
		}
	}

	private static void ttm(RobotController rc) {
		// do one time things here
		while (true) {
//			try {
				Clock.yield();				
//			} catch (GameActionException e) {
//				e.printStackTrace();
//			}
		}
	}

	private static void viper(RobotController rc) {
		// in case we use vipers later
		// do one time things here
		while (true) {
//			try {
				Clock.yield();				
//			} catch (GameActionException e) {
//				e.printStackTrace();
//			}
		}
	}

	/**
	 * To be run by archons during the intro phase (and others? change it a bit?) to handle and gather
	 * information from incoming message signals
	 * @param rc
	 * @param signals
	 * @param cornerX
	 * @param cornerY
	 * @param denX
	 * @param denY
	 */
	private static void processIntroMessageSignals(RobotController rc) {
		int cornerX = Integer.MIN_VALUE;
		int cornerY = Integer.MIN_VALUE;
		int denX = Integer.MIN_VALUE;
		int denY = Integer.MIN_VALUE;
		
		Signal[] signals = rc.emptySignalQueue();
		for (Signal s : signals) {
			if (s.getTeam() == rc.getTeam()) {
				if (s.getMessage() != null) {
					if (s.getMessage()[0] == SENDING_CORNER_X) {
						cornerX = s.getMessage()[1];
					} else if (s.getMessage()[0] == SENDING_CORNER_Y) {
						cornerY = s.getMessage()[1];
					} else if (s.getMessage()[0] == SENDING_DEN_X) {
						denX = s.getMessage()[1];
					} else if (s.getMessage()[0] == SENDING_DEN_Y) {
						denY = s.getMessage()[1];
					}
				}
			}
		}
		if (cornerX > Integer.MIN_VALUE && cornerY > Integer.MIN_VALUE) {
			MapLocation newCorner = new MapLocation(cornerX, cornerY);
			if (!CORNERS_ALREADY_SCOUTED.contains(newCorner)) {
				CORNERS_ALREADY_SCOUTED.add(newCorner);
				rc.setIndicatorString(0, "Added new corner: " + newCorner);
			}
			rc.setIndicatorString(1, CORNERS_ALREADY_SCOUTED + "");
		}
		if (denX > Integer.MIN_VALUE && denY > Integer.MIN_VALUE) {
			MapLocation newDen = new MapLocation(denX, denY);
			if (!ZOMBIE_DEN_LOCATIONS.contains(newDen)) {
				ZOMBIE_DEN_LOCATIONS.add(newDen);				
			}
		}
	}
	
	/**
	 * Best is determined by nearest to the archon initial center of mass
	 * @param rc
	 * @return
	 */
	private static MapLocation findBestTurtleCorner(RobotController rc) {
		List<MapLocation> archonLocations = Arrays.asList(rc.getInitialArchonLocations(rc.getTeam()));
		int avgX = 0;
		int avgY = 0;
		for (MapLocation loc : archonLocations) {
			avgX += loc.x;
			avgY += loc.y;
		}
		avgX /= archonLocations.size();
		avgY /= archonLocations.size();
		MapLocation archonCenterOfMass = new MapLocation(avgX, avgY);
		Optional<MapLocation> bestCorner = CORNERS_ALREADY_SCOUTED.stream().min((corner1, corner2) ->
		corner1.distanceSquaredTo(archonCenterOfMass) - corner2.distanceSquaredTo(archonCenterOfMass));
		return bestCorner.get();
	}

	/**
	 * If rc has no weapon delay, attacks the first robot sensed
	 * @param rc RobotController which will attack
	 * @throws GameActionException
	 * @return true if this robot attacked else false
	 */
	private static boolean attackFirst(RobotController rc) throws GameActionException {
		if (rc.isWeaponReady()) {
			RobotInfo[] enemies = rc.senseHostileRobots(rc.getLocation(), rc.getType().attackRadiusSquared);
			if (enemies.length > 0) {
				rc.attackLocation(enemies[0].location);
				return true;
			}
		}
		return false;
	}

	/**
	 * Attempts to build a robot of the given type in a random direction (attempts all directions).
	 * Only attempts building if rc has no core delay and has more parts than the threshold.
	 * @param rc RobotController from which to build
	 * @param buildType type of robot to build
	 * @return true if buildType is built else false
	 * @throws GameActionException 
	 */
	private static boolean tryToBuild(RobotController rc, RobotType buildType) throws GameActionException {
		if (rc.isCoreReady()) {
			Direction buildDir = getRandomDirection();
			for (int i = 0; i < 8; i++) {
				if (rc.canBuild(buildDir, buildType)) {
					rc.build(buildDir, buildType);
					return true;
				}
				buildDir = buildDir.rotateRight(); // try all directions clockwise
			}
		}
		return false;
	}
	
	/**
	 * Repairs the first-sensed, injured, allied robot
	 * @param rc must be an archon
	 * @throws GameActionException 
	 */
	private static void repairFirst(RobotController rc) throws GameActionException {
		RobotInfo[] allies = rc.senseNearbyRobots(rc.getType().attackRadiusSquared, rc.getTeam());
		for (int i = 0; i < allies.length; i++) {
			if (allies[i].health < allies[i].maxHealth && !allies[i].type.equals(RobotType.ARCHON)) { // can't repair other archons
				rc.repair(allies[i].location);
				break;
			}
		}
	}
	
	private static void leadZombiesToEnemy(RobotController rc) throws GameActionException {
		//TODO stop anti-kiting
		if (rc.isCoreReady()) {
			if (rc.canMove(Direction.SOUTH_WEST)) {
				rc.move(Direction.SOUTH_WEST);
			}
		}
	}
	
	/**
	 * Checks if rc can move in direction dir (runs isCoreReady and canMove). If so, moves.
	 * If not, moves rc in any of the four directions nearest dir, if possible.
	 * @param rc
	 * @param dir
	 * @return true if rc moves else false
	 * @throws GameActionException 
	 */
	private static boolean moveTowards(RobotController rc, Direction dir) throws GameActionException {
		if (rc.isCoreReady()) {
			Direction[] nearDirections = {dir, dir.rotateRight(), dir.rotateLeft(),
					dir.rotateRight().rotateRight(), dir.rotateLeft().rotateLeft()};
			for (Direction nearDir : nearDirections) {
				if (rc.canMove(nearDir)) {
					rc.move(nearDir);
					return true;
				}
			}
			if (rc.onTheMap(rc.getLocation().add(dir)) && rc.senseRubble(rc.getLocation().add(dir)) > RUBBLE_LOWER_CLEAR_THRESHOLD) {
				rc.clearRubble(dir);
			}
		}
		return false;
	}
	
	/**
	 * 
	 * @param rc
	 * @return true if rc moves else false
	 * @throws GameActionException
	 */
	private static boolean moveTowardsNearestEnemy (RobotController rc) throws GameActionException {
		List<RobotInfo> enemies = Arrays.asList(rc.senseHostileRobots(rc.getLocation(), -1));
		Optional<RobotInfo> nearestEnemy = enemies.stream().min((enemy1, enemy2) ->
		rc.getLocation().distanceSquaredTo(enemy1.location) - rc.getLocation().distanceSquaredTo(enemy2.location));
		if (nearestEnemy.isPresent()) {
			return moveTowards(rc, rc.getLocation().directionTo(nearestEnemy.get().location));
		}
		return false;
	}
	
	//TODO this method needs a lot of work...isn't doing things properly even if there's just an 
	// archon making bots with open space around it. Add onto that later, when there are more bots around,
	// others will need to move forward in order for those just next to the archon to move. 
	private static boolean moveAwayFromArchons (RobotController rc) throws GameActionException {
		List<RobotInfo> robots = Arrays.asList(rc.senseNearbyRobots(ARCHON_RESERVED_DISTANCE_SQUARED*2, rc.getTeam()));
		List<RobotInfo> archons = new ArrayList<>();
		for (RobotInfo robot : robots) {
			if (robot.type == RobotType.ARCHON) {
				archons.add(robot);
			}
		}
		boolean tooClose = false;
		MapLocation myLocation = rc.getLocation();
		for (RobotInfo archon : archons) {
			if (myLocation.distanceSquaredTo(archon.location) < ARCHON_RESERVED_DISTANCE_SQUARED) {
				tooClose = true;
				break;
			}
		}
		
		if (!tooClose) {
			return false;
		}
		
		List<MapLocation> possibleMoveLocations = Arrays.asList(MapLocation.getAllMapLocationsWithinRadiusSq(rc.getLocation(), 2));
		List<MapLocation> goodMoveLocations = new ArrayList<>();
		
		for (MapLocation loc : possibleMoveLocations) {
			boolean goodLocation = true;
			for (RobotInfo archon : archons) {
				if (loc.distanceSquaredTo(archon.location) < ARCHON_RESERVED_DISTANCE_SQUARED) {
					goodLocation = false;
					break;
				}
			}
			if (goodLocation) {
				goodMoveLocations.add(loc);
			}
		}
		
		for (MapLocation loc : goodMoveLocations) {
			if (rc.canMove(rc.getLocation().directionTo(loc))) {
				rc.move(rc.getLocation().directionTo(loc));
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Moves rc in direction dir if possible, while attempting to stay out of attack range
	 * of hostile robots.
	 * @param rc
	 * @param dir
	 */
	private static void moveCautiously(RobotController rc, Direction dir) {
		//TODO sense hostile robots and their attack ranges, make mapping between mapLocations you could
		// move to and the amount of damage you would take if all robots that can attack that square did
		// (remember, though, they will get to move one more time before you do)
		// pick a square in the direction you want to go but where you'll take less/least damage
	}
	
	private static final Map<Integer, Direction> SIGNAL_TO_DIRECTION = new HashMap<Integer, Direction>();
	static {
		SIGNAL_TO_DIRECTION.put(NORTH_WEST_CORNER, Direction.NORTH_WEST);
		SIGNAL_TO_DIRECTION.put(NORTH_EAST_CORNER, Direction.NORTH_EAST);
		SIGNAL_TO_DIRECTION.put(SOUTH_EAST_CORNER, Direction.SOUTH_EAST);
		SIGNAL_TO_DIRECTION.put(SOUTH_WEST_CORNER, Direction.SOUTH_WEST);
	}
	private static Direction getScoutTargetDirection(RobotController rc) {
		Signal[] signals =  rc.emptySignalQueue();
		for (Signal signal : signals) {
			if (signal.getMessage() != null && signal.getTeam().equals(rc.getTeam())) {
				if (signal.getMessage()[0] == SENDING_TARGET_DIRECTION) {
					return SIGNAL_TO_DIRECTION.get(signal.getMessage()[1]);
				}
			}
		}
		return Direction.NONE;
	}
	
	private static final Map<Direction, Integer> DIRECTION_TO_SIGNAL = new HashMap<Direction, Integer>();
	static {
		DIRECTION_TO_SIGNAL.put(Direction.NORTH_WEST, NORTH_WEST_CORNER);
		DIRECTION_TO_SIGNAL.put(Direction.NORTH_EAST, NORTH_EAST_CORNER);
		DIRECTION_TO_SIGNAL.put(Direction.SOUTH_WEST, SOUTH_WEST_CORNER);
		DIRECTION_TO_SIGNAL.put(Direction.SOUTH_EAST, SOUTH_EAST_CORNER);
	}
	/**
	 * Currently we use two intro scouts, so this method identifies two opposite corners to which
	 * those scouts should be sent. So that no extra communication is needed, one archon will calculate
	 * one of the corners as closest for him and all of the other archons will calculate the other corner
	 * so that the two scouts are guaranteed to move towards opposite corners.
	 * @param rc must be an archon in its initial location and should have built nothing except scouts yet
	 * @return
	 */
	private static Direction calculateDirectionToClosestCorner(RobotController rc) {
		// TODO determine which corner pair is better to use 
		Direction toTarget = Direction.NONE;
		List<MapLocation> archonLocations = Arrays.asList(rc.getInitialArchonLocations(rc.getTeam()));
		if (archonLocations.size() == 1) { // this one must send a scout to both corners
			if(rc.getRobotCount() == 1) {
				toTarget = Direction.NORTH_EAST;
			} else {
				toTarget = Direction.SOUTH_WEST;
			}
		} else {
			Optional<MapLocation> northernMost = archonLocations.stream().min((a, b) -> a.y - b.y); 
			if (rc.getLocation().equals(northernMost.get())) {
				return Direction.NORTH_EAST;
			} else {
				return Direction.SOUTH_WEST;
			}
		}
		return toTarget;
	}
	
	private static MapLocation getFurthestInDirection(RobotController rc, MapLocation[] locs, Direction dir) throws GameActionException {
		MapLocation furthest = null;
		List<Direction> directionsTowards = Arrays.asList(dir, dir.rotateRight(), dir.rotateLeft());
		for (MapLocation loc : locs) {
			if (furthest == null) {
				if (rc.onTheMap(loc)) {
					furthest = loc;					
				}
			} else if (directionsTowards.contains(furthest.directionTo(loc)) && rc.onTheMap(loc)) {
				furthest = loc;
			}
		}
		return furthest;
	}
	
	/**
	 * (Returning null is bad practice...but they didn't give us a MapLocation.NONE or such =\ )
	 * @param rc
	 * @param dirToCorner
	 * @return the location of the corner in the given direction or null if it is not a corner
	 * @throws GameActionException
	 */
	private static MapLocation checkForCorner(RobotController rc, Direction dirToCorner) throws GameActionException {
		int senseRadiusMinusOneSquared = (int) Math.pow(Math.sqrt(rc.getType().sensorRadiusSquared)-1, 2);
		// so that when you add one below to check for a corner, you can still sense the +1 location
		MapLocation[] nearby = MapLocation.getAllMapLocationsWithinRadiusSq(rc.getLocation(), senseRadiusMinusOneSquared);
		boolean isCorner = true;
		MapLocation corner;
		Direction[] nearDirections = {dirToCorner, dirToCorner.rotateLeft(), dirToCorner.rotateRight()};
		corner = getFurthestInDirection(rc, nearby, dirToCorner);
		for (Direction dir : nearDirections) {
			if (rc.onTheMap(corner.add(dir))) {
				isCorner = false;
			}
		}
		if (isCorner) {
			return corner;
		} else {
			return null;
		}
	}
	
	/**
	 * @return a random direction from DIRECTIONS
	 */
	private static Direction getRandomDirection() {
		return DIRECTIONS[(int) rand.nextDouble()*9];
	}
}
