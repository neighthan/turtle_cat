package team362;

import java.util.Random;

import battlecode.common.*;

public class RobotPlayer {
	
	private static boolean turtleMode = false; // have intro phase before engaging turtle mode
	private static int turtleCornerX = 0;
	private static int turtleCornerY = 0;
	private final static int PARTS_THRESHOLD = 100;
	private static final Direction[] DIRECTIONS = {Direction.NORTH, Direction.NORTH_EAST, Direction.EAST,
			Direction.SOUTH_EAST, Direction.SOUTH, Direction.SOUTH_WEST, Direction.WEST, Direction.NORTH_WEST};
	private static Random rand;
	
	private final static int NUM_INTRO_SCOUTS = 4;
	private static int archonFullMapRadius = 181;
	private static int scoutFullMapRadius = 119;
	private static int numArchons = 4; // assume 4 until you know at the 2nd turn
	
	// Commands for signals
	private static final int SENDING_NUM_ARCHONS = 0;
	private static final int SENDING_DEN_X = 1;
	private static final int SENDING_DEN_Y = 2;
	private static final int SENDING_TURTLE_X = 3;
	private static final int SENDING_TURTLE_Y = 4;
	
	
	public static void run(RobotController rc) {
		rand = new Random(rc.getID());
		try {
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
		} catch (GameActionException e) {
			e.printStackTrace();
		}
	}

	/**
	 * TODO - about archon strategy here
	 * @param rc
	 * @throws GameActionException
	 */
	private static void archon(RobotController rc) throws GameActionException {
		while (true) {
			if (rc.getRoundNum() == 1) {
				rc.broadcastSignal(archonFullMapRadius);
				Clock.yield(); // TODO don't need to skip a turn but don't let there be other broadcasts this turn
			} else if (rc.getRoundNum() == 2) {
				Signal[] signals = rc.emptySignalQueue();
				for (Signal signal : signals) {
					if (signal.getTeam() == rc.getTeam()) {
						numArchons += 1;
					}
				}
				rc.setIndicatorString(0, numArchons + "");
			}
			//TODO broadcast numArchons when you start each intro scout (small broadcast) so they can 
			// get their numbers
			if (!turtleMode) {
				// TODO should also repair during this mode if they can?
				// TODO gathering parts (need locations from scouts first...)
				if (rc.getRobotCount() - numArchons < NUM_INTRO_SCOUTS) {
					tryToBuild(rc, RobotType.SCOUT);
					rc.broadcastMessageSignal(SENDING_NUM_ARCHONS, numArchons, 0);
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
		}
	}

	private static int scoutNumber = 0; // 1=NW; 2=NE; 3=SE; 4=SW
	private static void scout(RobotController rc) {
		// do one time things here
		while (true) {
			if (!turtleMode) {
				if (scoutNumber == 0) {
					Signal[] signals = rc.emptySignalQueue();
					for (Signal signal : signals) {
						if (signal.getMessage() != null && signal.getTeam() == rc.getTeam()) {
							if (signal.getMessage()[0] == SENDING_NUM_ARCHONS) {
								numArchons = signal.getMessage()[1];
								scoutNumber = rc.getRobotCount() - numArchons;
								rc.setIndicatorString(0, scoutNumber + "");													
							}							
						}
					}
				}
			}
			Clock.yield();
		}
	}

	private static void guard(RobotController rc) throws GameActionException {
		// do one time things here
		while (true) {
			if (!turtleMode) {
				//TODO smarter attacking (make a method to pick and attack)
				attackFirst(rc);
			}
			Clock.yield();
		}
	}

	private static void soldier(RobotController rc) throws GameActionException {
		// do one time things here
		while (true) {
			if (!turtleMode) {
				//TODO smarter attacking (make a method to pick and attack)
				attackFirst(rc);
			}
			Clock.yield();
		}
	}

	private static void turret(RobotController rc) {
		// do one time things here
		while (true) {
			Clock.yield();
		}
	}

	private static void ttm(RobotController rc) {
		// do one time things here
		while (true) {
			Clock.yield();
		}
	}

	private static void viper(RobotController rc) {
		// in case we use vipers later
		// do one time things here
		while (true) {
			Clock.yield();
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
		if (rc.isCoreReady() && rc.getTeamParts() >= PARTS_THRESHOLD) {
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
	
	/**
	 * @return a random direction from DIRECTIONS
	 */
	private static Direction getRandomDirection() {
		return DIRECTIONS[(int) rand.nextDouble()*9];
	}
}
