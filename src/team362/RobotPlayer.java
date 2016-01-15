package team362;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import battlecode.common.*;

public class RobotPlayer {
	
	// Strategic stages
	private static boolean introMode = true;
	private static boolean transitionMode = false;
	private static boolean turtleMode = false;
	
	private static int turtleCornerX = 0;
	private static int turtleCornerY = 0;
//	private final static int PARTS_THRESHOLD = 100;
	private static final Direction[] DIRECTIONS = {Direction.NORTH, Direction.NORTH_EAST, Direction.EAST,
			Direction.SOUTH_EAST, Direction.SOUTH, Direction.SOUTH_WEST, Direction.WEST, Direction.NORTH_WEST};
	private static Random rand;
	
	private final static int NUM_INTRO_SCOUTS = 2;
	private static int archonFullMapRadius = 181; // TODO - un-magic number
	private static int scoutFullMapRadius = 119; // TODO - change these once we know map size?
	private static int numArchons = 0;
	
	private static final int NUM_TURNS_TO_CHECK_FOR_ZOMBIES = 3;
	
	private static final List<MapLocation> ZOMBIE_DEN_LOCATIONS = new ArrayList<>();
	//TODO stop trying to use shared memory; broadcast (above and below)
	
	private static final Map<Direction, MapLocation> CORNERS = new HashMap<>();
	private static final List<Direction> CORNERS_ALREADY_SCOUTED = new ArrayList<>();
	
	
	// Commands for signals
	private static final int SENDING_DEN_X = 1;
	private static final int SENDING_DEN_Y = 2;
	private static final int SENDING_TURTLE_X = 3;
	private static final int SENDING_TURTLE_Y = 4;
	private static final int SENDING_TARGET_DIRECTION = 5;
	
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
					// TODO gathering parts (need locations from scouts first...)
					if (rc.getRobotCount() - numArchons < NUM_INTRO_SCOUTS) {
						tryToBuild(rc, RobotType.SCOUT);
						Direction directionToTargetCorner = calculateDirectionToClosestCorner(rc);
						rc.broadcastMessageSignal(SENDING_TARGET_DIRECTION, DIRECTION_TO_SIGNAL.get(directionToTargetCorner), 4);
						CORNERS_ALREADY_SCOUTED.add(directionToTargetCorner);
					} else {
						if (rand.nextDouble() > .66666) { // 2:1 soldier:guard ratio
							tryToBuild(rc, RobotType.GUARD);
						} else {
							tryToBuild(rc, RobotType.SOLDIER);
						}
					}
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
							ZOMBIE_DEN_LOCATIONS.add(zombie.location);
						}
					}
					MapLocation corner = checkForCorner(rc, targetDirection);
					if (corner != null) {
						CORNERS.put(targetDirection, corner);
						rc.setIndicatorString(0, CORNERS + "");
						if (CORNERS.size() == 4) {
							introMode = false;
							transitionMode = true;
						} else { // head to next nearest corner
							if (!CORNERS_ALREADY_SCOUTED.contains(targetDirection.rotateLeft().rotateLeft())) {
								targetDirection = targetDirection.rotateLeft().rotateLeft();
							} else if (!CORNERS_ALREADY_SCOUTED.contains(targetDirection.rotateRight().rotateRight())) {
								targetDirection = targetDirection.rotateRight().rotateRight();
							} else {
								targetDirection = targetDirection.opposite();
							}
							CORNERS_ALREADY_SCOUTED.add(targetDirection);
						}
					} else {
						moveTowards(rc, targetDirection);						
					}
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
					attackFirst(rc);
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
					attackFirst(rc);
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
	 * If rc has no weapon delay, attacks the first robot sensed
	 * @param rc RobotController which will attack
	 * @throws GameActionException
	 */
	private static void attackFirst(RobotController rc) throws GameActionException {
		if (rc.isWeaponReady()) {
			RobotInfo[] enemies = rc.senseHostileRobots(rc.getLocation(), rc.getType().attackRadiusSquared);
			if (enemies.length > 0) {
				rc.attackLocation(enemies[0].location);
			}
		}
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
			if (allies[i].health < allies[i].maxHealth) {
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
	 * @throws GameActionException 
	 */
	private static void moveTowards(RobotController rc, Direction dir) throws GameActionException {
		if (rc.isCoreReady()) {
			Direction[] nearDirections = {dir, dir.rotateRight(), dir.rotateLeft(),
					dir.rotateRight().rotateRight(), dir.rotateLeft().rotateLeft()};
			for (Direction nearDir : nearDirections) {
				if (rc.canMove(nearDir)) {
					rc.move(nearDir);
					return;
				}
			}
		}
	}
	
	/**
	 * Moves rc in direction dir if possible, while attempting to stay out of attack range
	 * of hostile robots.
	 * @param rc
	 * @param dir
	 */
	private static void moveCautiously(RobotController rc, Direction dir) {
		
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
	private static Direction calculateDirectionToClosestCorner(RobotController rc) {
		Direction toTarget = Direction.NONE;
		MapLocation[] archonLocations = rc.getInitialArchonLocations(rc.getTeam());
		if (rc.getLocation().y >= getSecondHighestYValue(archonLocations)) {
			if (rc.getLocation().x >= getSecondHighestXValue(archonLocations)) {
				toTarget = Direction.NORTH_EAST;
			} else {
				toTarget = Direction.NORTH_WEST;
			}
		} else {
			if (rc.getLocation().x >= getSecondHighestXValue(archonLocations)) {
				toTarget = Direction.SOUTH_EAST;
			} else {
				toTarget = Direction.SOUTH_WEST;
			}
		}
		return toTarget;
	}
	
	//TODO streamline these methods somehow...highest/second highest
	
	private static int getSecondHighestXValue(MapLocation[] locs) {
		int highest = Integer.MIN_VALUE;
		int secondHighest = Integer.MIN_VALUE;
		for (MapLocation loc : locs) {
			if (loc.x > highest) {
				secondHighest = highest;
				highest = loc.x;
			} else if (loc.x > secondHighest) {
				secondHighest = loc.x;
			}
		}
		return secondHighest;
	}
	
	private static int getSecondHighestYValue(MapLocation[] locs) {
		int highest = Integer.MIN_VALUE;
		int secondHighest = Integer.MIN_VALUE;
		for (MapLocation loc : locs) {
			if (loc.y > highest) {
				secondHighest = highest;
				highest = loc.y;
			} else if (loc.y > secondHighest) {
				secondHighest = loc.y;
			}
		}
		return secondHighest;
	}
	
	// TODO x and y viewed as increasing in wrong directions??
	private static MapLocation getMostNE(RobotController rc, MapLocation[] locs) throws GameActionException {
		MapLocation NE = null;
		for (MapLocation loc : locs) {
			if (NE == null && rc.onTheMap(loc)) {
				NE = loc;
			} else if ((loc.x > NE.x || loc.y > NE.y) && rc.onTheMap(loc)) {
				NE = loc;
			}
		}
		return NE;
	}
	
	private static MapLocation getMostNW(RobotController rc, MapLocation[] locs) throws GameActionException {
		MapLocation NW = null;
		for (MapLocation loc : locs) {
			if (NW == null && rc.onTheMap(loc)) {
				NW = loc;
			} else if ((loc.x < NW.x || loc.y > NW.y) && rc.onTheMap(loc)) {
				NW = loc;
			}
		}
		return NW;
	}
	
	private static MapLocation getMostSE(RobotController rc, MapLocation[] locs) throws GameActionException {
		MapLocation SE = null;
		for (MapLocation loc : locs) {
			if (SE == null && rc.onTheMap(loc)) {
				SE = loc;
			} else if ((loc.x > SE.x || loc.y < SE.y) && rc.onTheMap(loc)) {
				SE = loc;
			}
		}
		return SE;
	}
	
	private static MapLocation getMostSW(RobotController rc, MapLocation[] locs) throws GameActionException {
		MapLocation SW = null;
		for (MapLocation loc : locs) {
			if (SW == null && rc.onTheMap(loc)) {
				SW = loc;
			} else if ((loc.x < SW.x || loc.y < SW.y) && rc.onTheMap(loc)) {
				SW = loc;
			}
		}
		return SW;
	}
	
	/**
	 * (Returning null is bad practice...but they didn't give us a MapLocation.NONE or such =\ )
	 * @param rc
	 * @param dirToCorner
	 * @return the location of the corner in the given direction or null if it is not a corner
	 * @throws GameActionException
	 */
	private static MapLocation checkForCorner(RobotController rc, Direction dirToCorner) throws GameActionException {
		MapLocation[] nearby = MapLocation.getAllMapLocationsWithinRadiusSq(rc.getLocation(), rc.getType().attackRadiusSquared);
		boolean isCorner = true;
		MapLocation corner;
		if (dirToCorner.equals(Direction.NORTH_EAST)) {
			corner = getMostNE(rc, nearby);
			Direction[] dirs = {Direction.NORTH, Direction.NORTH_EAST, Direction.EAST};
			for (Direction dir : dirs) {
				if (rc.onTheMap(corner.add(dir))) {
					isCorner = false;
				}
			}
		} else if (dirToCorner.equals(Direction.NORTH_WEST)) {
			corner = getMostNW(rc, nearby);
			Direction[] dirs = {Direction.NORTH, Direction.NORTH_WEST, Direction.WEST};
			for (Direction dir : dirs) {
				if (rc.onTheMap(corner.add(dir))) {
					isCorner = false;
				}
			}
		} else if (dirToCorner.equals(Direction.SOUTH_WEST)) {
			corner = getMostSW(rc, nearby);
			Direction[] dirs = {Direction.SOUTH, Direction.SOUTH_WEST, Direction.WEST};
			for (Direction dir : dirs) {
				if (rc.onTheMap(corner.add(dir))) {
					isCorner = false;
				}
			}
		} else { // SE
			corner = getMostSE(rc, nearby);
			Direction[] dirs = {Direction.SOUTH, Direction.SOUTH_EAST, Direction.EAST};
			for (Direction dir : dirs) {
				if (rc.onTheMap(corner.add(dir))) {
					isCorner = false;
				}
			}
		}
		if (!rc.onTheMap(corner)) {
			isCorner = false;
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
