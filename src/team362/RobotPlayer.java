package team362;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import battlecode.common.*;

public class RobotPlayer {
	
	
	private static MapLocation turtleCorner;
//	private final static int PARTS_THRESHOLD = 100;
	private static final int RUBBLE_LOWER_CLEAR_THRESHOLD = 40;
	private static final Direction[] DIRECTIONS = {Direction.NORTH, Direction.NORTH_EAST, Direction.EAST,
			Direction.SOUTH_EAST, Direction.SOUTH, Direction.SOUTH_WEST, Direction.WEST, Direction.NORTH_WEST};
	private static final Direction[] OPPOSITE_DIRECTIONS = {Direction.SOUTH, Direction.SOUTH_WEST, Direction.WEST, 
			Direction.NORTH_WEST, Direction.NORTH, Direction.NORTH_EAST, Direction.EAST, Direction.SOUTH_EAST};
	private static Random rand;
	private final static MapLocation LOCATION_NONE = new MapLocation(Integer.MAX_VALUE, Integer.MAX_VALUE);
	// because there is no .NONE; MapLocations are only offset by +/- 500, and size <= 80, so this won't be taken
	
	private final static int NUM_INTRO_SCOUTS = 4;
	private static int NUM_KITING_SCOUTS = 4;
	private static final int ARCHON_RESERVED_DISTANCE_SQUARED = 9;
	private static final int ONE_SQUARE_RADIUS = 2;
	private static int fullMapRadius = GameConstants.MAP_MAX_HEIGHT*GameConstants.MAP_MAX_WIDTH;
	private static int numArchons = 0;
	private static Team myTeam;
	
	private static final int NUM_TURNS_TO_CHECK_FOR_ZOMBIES = 3;
	
	private static final List<MapLocation> ZOMBIE_DEN_LOCATIONS = new ArrayList<>();
	private static final List<MapLocation> SCOUTED_CORNERS = new ArrayList<>();
	
	// First arguments for signals - identifiers of what will be sent
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
	private static final int SENDING_MODE = 11;
	
	// Second arguments for signals - meanings of the number sent
	private static final int INTRO_MODE = 0;
	private static final int TRANSITION_MODE = 1;
	private static final int TURTLE_MODE = 2;
	private static final int NORTH_EAST_CORNER = 3;
	private static final int NORTH_WEST_CORNER = 4;
	private static final int SOUTH_EAST_CORNER = 5;
	private static final int SOUTH_WEST_CORNER = 6;	
	
	private static int currentMode = INTRO_MODE;
	
	/**
	 * @param rc
	 */
	public static void run(RobotController rc) 
	{
		rand = new Random(rc.getID());
		myTeam = rc.getTeam();
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
	 * @Nathan - Should we adjust these priorities (specifically where should building troops be?)
	 * TODO for archons - 
	 * 1) repair, always run away from enemies (excluding scouts)
	 * 2) signal turtle location
	 * 3) pick up adjacent parts and activate neutral units
	 * 4) move to turtle corner
	 * 5) build troops
	 * @param rc
	 * @throws GameActionException
	 */
	private static void archon(RobotController rc){
		numArchons = rc.getInitialArchonLocations(myTeam).length;
		while (true) {
			try {
				// 1) repair nearest injured unit
				// if senses enemy troop that is not a scout, run away from it
			repairFirst(rc);
			moveAwayFromEnemies(rc);
				// 2) signal turtle location - every 25 turns 
			int broadcastRadius = rc.getType().sensorRadiusSquared*(2+(rc.getRobotCount()/50));
			if (rc.getRoundNum() > 90 && rc.getRoundNum() % 25 == 0 && turtleCorner != null)
			{
				// @Hope - fix broadcast signal range - make it calculated based on number of troops
				rc.broadcastMessageSignal(turtleCorner.x, turtleCorner.y, broadcastRadius);
			}
				// 3) Pick up parts on current square, check for adjacent parts and try to collect them
			if (rc.isCoreReady()) collectParts(rc);
				// 3a) activate any nearby units
			if (rc.isCoreReady()) activateUnits(rc);
			
			// @Nathan - Intro mode is all you -- I just pasted all the text into a separate method for clarity
				if (currentMode == INTRO_MODE) 
				{
					archonIntroMode(rc);
				} 
				// 4) Move to turtle corner
				if (currentMode == TRANSITION_MODE) 
				{
					if (rc.getLocation().distanceSquaredTo(turtleCorner) > 4) 
					{
						moveTowards(rc, rc.getLocation().directionTo(turtleCorner));						
					} 
					else 
					{
						// TODO get in proper position
						currentMode = TURTLE_MODE;
						rc.broadcastMessageSignal(SENDING_MODE, TURTLE_MODE, rc.getType().sensorRadiusSquared);
					}
				}
					
				// 5) Build Troops !
				// @Nathan do we need to build more scouts in turtle mode so we can do more kiting?
				if (rc.getRobotCount() - numArchons < NUM_INTRO_SCOUTS && rc.isCoreReady()) 
				{
					tryToBuild(rc, RobotType.SCOUT);
					Direction directionToTargetCorner = calculateDirectionToClosestCorner(rc);
					rc.broadcastMessageSignal(SENDING_TARGET_DIRECTION, DIRECTION_TO_SIGNAL.get(directionToTargetCorner), 4);
				} 
				if (rc.getRobotCount() <= 30 && rc.isCoreReady()) tryToBuild(rc, RobotType.SOLDIER);
				if (rc.getRobotCount() > 30 && rc.isCoreReady()) tryToBuild(rc, RobotType.TTM);
				Clock.yield(); // end turn
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
				// TODO - I've made it so the scouts will go in pairs now; not sure if we want this later or not, but they
				// aren't avoiding enemies well enough
				if (currentMode == INTRO_MODE && rc.getRoundNum() >= 40) {
					// TODO - scouts don't pick their directions well enough - try running diffusion.
					// one often starts already in/near a corner; if we find a corner very close, we should probably just move to it
					// and turtle right away (even if we don't know about the others yet)
					if (targetDirection.equals(Direction.NONE)) {
						targetDirection = getScoutTargetDirection(rc);
					}
					
//					RobotInfo[] zombies = rc.senseNearbyRobots(-1, Team.ZOMBIE);
					// TODO: bring this back in, but only if there aren't bots that will attack the scout
					// (otherwise, the delay makes them too vulnerable; finding corners matters more first)
//					for (RobotInfo zombie : zombies) {
//						if (zombie.type == RobotType.ZOMBIEDEN) {
//							if (!ZOMBIE_DEN_LOCATIONS.contains(zombie.location)) {
//								ZOMBIE_DEN_LOCATIONS.add(zombie.location);
//								rc.broadcastMessageSignal(SENDING_DEN_X, zombie.location.x, fullMapRadius);
//								rc.broadcastMessageSignal(SENDING_DEN_Y, zombie.location.y, fullMapRadius);
//							}
//						}
//					}
//					int start = Clock.getBytecodeNum();
					MapLocation corner = checkForCorner(rc, targetDirection);
//					System.out.println("CheckforCorner: " + (Clock.getBytecodeNum() - start));
					if (corner != null) {
						rc.broadcastMessageSignal(SENDING_CORNER_X, corner.x, fullMapRadius);
						rc.broadcastMessageSignal(SENDING_CORNER_Y, corner.y, fullMapRadius);
						// head to opposite in case other scout didn't make it
						// TODO or scout around for other zombie dens, kite, etc.
						targetDirection = targetDirection.opposite();
					}
//					start = Clock.getBytecodeNum();
					moveCautiously(rc, targetDirection);
//					System.out.println("moveCautiously: " + (Clock.getBytecodeNum() - start));
				} else { // not intro mode; do kiting
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
				if (currentMode == INTRO_MODE) {
					//TODO smarter attacking (make a method to pick and attack, move if appropriate)
					if (rc.isWeaponReady()) attackFirst(rc);
					if (rc.isCoreReady()) {
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
/**
	 * Working on soldier behavior:
	 * 1) If about to die and infected and senses nearby friendly robots, self destruct
	 * 2) Attack if possible
	 * 3) Process signals. 
	 *  (If another soldier signals, move towards that soldier (means there is a zombie den nearby))
	 *  (Use archon signals to determine whether to move towards archon or away.)
	 * 4) Try to clear surrounding rubble 
	 * 
	 * @param rc
	 */
	private static void soldier(RobotController rc)
	{
		MapLocation turtleCorner = LOCATION_NONE;
		while (true) 
		{
			try 
			{
				RobotInfo[] friendlyRobots = rc.senseNearbyRobots(rc.getType().sensorRadiusSquared, rc.getTeam());
				if (rc.getHealth() <= 3 && rc.isInfected() && friendlyRobots.length > 0) // 1)
				{
					rc.disintegrate();
				}
				else
				{
					
					if (rc.isWeaponReady()) attackFirst(rc); // 2)
					processFighterSignals(rc); //3)
					if (turtleCorner != LOCATION_NONE)
					{
						int minRadius = 25;
						int maxRadius = 25 + rc.getRobotCount()/7;
						int distanceToCorner = rc.getLocation().distanceSquaredTo(turtleCorner);
						if (distanceToCorner <= minRadius && rc.isCoreReady())
						{
							Direction dir = oppositeDirection(rc.getLocation().directionTo(turtleCorner));
							moveTowards(rc, dir);
						}
						if (distanceToCorner >= maxRadius && rc.isCoreReady())
						{
							moveTowards(rc, rc.getLocation().directionTo(turtleCorner));
						}
					}
					else
					{
						if(rc.isCoreReady() && currentMode == TRANSITION_MODE) moveTowardsArchon(rc);
					}
					//if (rc.isCoreReady()) clearRubble(rc); // 4)
				}
				Clock.yield();				
			} 
			catch (GameActionException e) 
			{
				e.printStackTrace();
			}
		}
	}

	private static Direction oppositeDirection(Direction directionTo) 
	{
		int directionIndex = 0;
		for (int i=0; i<DIRECTIONS.length; i++)
		{
			if (directionTo == DIRECTIONS[i])
			{
				directionIndex = i;
			}
		}
		return OPPOSITE_DIRECTIONS[directionIndex];
	}

	/**
	 * If rc has no core delay, attempts to move towards the first allied archon sensed.
	 * Does not move if rc is already within ARCHON_RESERVED_DISTANCE_SQUARED of the archon
	 * @param rc
	 * @return true if rc moves else false
	 * @throws GameActionException
	 */
	private static void moveTowardsArchon(RobotController rc) throws GameActionException 
	{
		RobotInfo[] nearbyRobots =  rc.senseNearbyRobots(rc.getType().sensorRadiusSquared, myTeam);
		for (int i=0; i<nearbyRobots.length; i++) 
		{
			if(nearbyRobots[i].type.equals(RobotType.ARCHON) && rc.isCoreReady()) 
			{
				if (nearbyRobots[i].location.distanceSquaredTo(rc.getLocation()) > ARCHON_RESERVED_DISTANCE_SQUARED) 
				{
					Direction dir = rc.getLocation().directionTo(nearbyRobots[i].location);
					moveTowards(rc, dir);
				}
			}
		}
    }

	private static void turret(RobotController rc) 
	{
		// do one time things here
		while (true) {
			try {
				boolean attacked = turretAttack(rc);
				if (!attacked && rc.isWeaponReady()) {
					Signal[] signals = rc.emptySignalQueue();
					for (Signal s : signals) {
						if (!s.getTeam().equals(myTeam) && rc.canAttackLocation(s.getLocation())) {
							rc.attackLocation(s.getLocation());
							break;
						}
					}
				}
				Clock.yield();				
			} catch (GameActionException e) {
				e.printStackTrace();
			}
		}
	}

	private static void ttm(RobotController rc) 
	{
		// do one time things here
		MapLocation turtleCorner = LOCATION_NONE;
		while (true) 
		{
			try 
			{
				moveAwayFromArchons(rc);
				processFighterSignals(rc);
				if (turtleCorner != LOCATION_NONE)
				{
					int minRadius = 25;
					int maxRadius = 25 + rc.getRobotCount()/7;
					int distanceToCorner = rc.getLocation().distanceSquaredTo(turtleCorner);
					if (distanceToCorner <= minRadius && rc.isCoreReady())
					{
						Direction dir = oppositeDirection(rc.getLocation().directionTo(turtleCorner));
						moveTowards(rc, dir);
					}
					if (distanceToCorner >= maxRadius && rc.isCoreReady())
					{
						moveTowards(rc, rc.getLocation().directionTo(turtleCorner));
					}
				}
				if (rc.isCoreReady()) rc.unpack();
				Clock.yield();				
			} 
			catch (GameActionException e) 
			{
				e.printStackTrace();
			}
		}
	}

	private static void viper(RobotController rc) {
		// in case we use vipers later
		// do one time things here
		while (true) 
		{
	    	rc.disintegrate();
			Clock.yield();				
		}
	}

	/**
	 * To be run by archons during the intro phase (and others? change it a bit?) to handle and gather
	 * information from incoming message signals
	 * @param rc
	 */
	private static void processIntroMessageSignals(RobotController rc) {
		int cornerX = Integer.MIN_VALUE;
		int cornerY = Integer.MIN_VALUE;
		int denX = Integer.MIN_VALUE;
		int denY = Integer.MIN_VALUE;
		
		Signal[] signals = rc.emptySignalQueue();
		for (Signal s : signals) {
			if (s.getTeam().equals(myTeam) && s.getMessage() != null) {
				final int[] message = s.getMessage();
				if (message[0] == SENDING_CORNER_X) {
					cornerX = message[1];
				} else if (message[0] == SENDING_CORNER_Y) {
					cornerY = message[1];
				} else if (message[0] == SENDING_DEN_X) {
					denX = message[1];
				} else if (message[0] == SENDING_DEN_Y) {
					denY = message[1];
				}
			}
		}
		if (cornerX > Integer.MIN_VALUE && cornerY > Integer.MIN_VALUE) {
			MapLocation newCorner = new MapLocation(cornerX, cornerY);
			if (!SCOUTED_CORNERS.contains(newCorner)) {
				SCOUTED_CORNERS.add(newCorner);
				rc.setIndicatorString(0, "Added new corner: " + newCorner);
			}
			rc.setIndicatorString(1, SCOUTED_CORNERS + "");
		}
		if (denX > Integer.MIN_VALUE && denY > Integer.MIN_VALUE) {
			MapLocation newDen = new MapLocation(denX, denY);
			if (!ZOMBIE_DEN_LOCATIONS.contains(newDen)) {
				ZOMBIE_DEN_LOCATIONS.add(newDen);				
			}
		}
	}
	
	/**
	 * Message-processing for non-archons
	 * Currently handles messages:
	 *    Change of mode
	 *    Setting turtle-corner location
	 * @param rc
	 * @throws GameActionException 
	 */
	private static void processFighterSignals(RobotController rc) throws GameActionException 
	{
		int cornerX = Integer.MIN_VALUE;
		int cornerY = Integer.MIN_VALUE;

		Signal[] signals = rc.emptySignalQueue();
		for (Signal s : signals) 
		{
			if (s.getTeam().equals(myTeam) && s.getMessage() != null) 
			{
				final int[] message = s.getMessage();
				if (message[0] == SENDING_MODE) {
					currentMode = message[1];
				}else if (message[0] == SENDING_TURTLE_X) {
					cornerX = message[1];
				} else if (message[0] == SENDING_TURTLE_Y) {
					cornerY = message[1];
				}
			}
			if (rc.getType() == RobotType.SOLDIER && s.getTeam().equals(myTeam) 
					&& s.getMessage() == null && rc.isCoreReady())
			{
				moveTowards(rc, rc.getLocation().directionTo(s.getLocation()));
			}
		}
		
		if (cornerX > Integer.MIN_VALUE && cornerY > Integer.MIN_VALUE) 
		{
			turtleCorner = new MapLocation(cornerX, cornerY);
		}
	}
	
	/**
	 * Best is determined by nearest to the archon initial center of mass
	 * @param rc
	 * @return
	 */
	private static MapLocation findBestTurtleCorner(RobotController rc) {
		List<MapLocation> archonLocations = Arrays.asList(rc.getInitialArchonLocations(myTeam));
		int avgX = 0;
		int avgY = 0;
		for (MapLocation loc : archonLocations) {
			avgX += loc.x;
			avgY += loc.y;
		}
		avgX /= archonLocations.size();
		avgY /= archonLocations.size();
		MapLocation archonCenterOfMass = new MapLocation(avgX, avgY);
		Optional<MapLocation> bestCorner = SCOUTED_CORNERS.stream().min((corner1, corner2) ->
		corner1.distanceSquaredTo(archonCenterOfMass) - corner2.distanceSquaredTo(archonCenterOfMass));
		return bestCorner.get();
	}

	/**
	 * If rc has no weapon delay, attacks the first robot sensed
	 * @param rc RobotController which will attack
	 * @param RobotController 
	 * @throws GameActionException
	 * @return true if this robot attacked else false
	 */
	private static void attackFirst(RobotController rc) throws GameActionException 
	{

		//boolean equalHealth = true;
		//int lowestHealthIndex = 0;
		int lowestDistanceIndex = 0;
		int attackIndex = -100000;
		RobotInfo[] enemies = rc.senseHostileRobots(rc.getLocation(), rc.getType().attackRadiusSquared);
		if (rc.isWeaponReady() && enemies.length > 0)
		{
			for (int i = 0; attackIndex < 0 && i<enemies.length; i++)
			{
				if (enemies[i].type == RobotType.ZOMBIEDEN)
				{
					rc.broadcastSignal(rc.getType().sensorRadiusSquared*2);
				}
				if (rc.canAttackLocation(enemies[i].location))
				{
					if (attackIndex < 0 && (rc.getLocation()).isAdjacentTo(enemies[i].location))
					{
						attackIndex = i;
						//TODO test this part - work on kiting
						if ((enemies[i].type == RobotType.BIGZOMBIE || enemies[i].type == RobotType.STANDARDZOMBIE)
								&& rc.getRoundNum()%2 == 0 && rc.isCoreReady())
						{
							moveAwayFromEnemy(rc, rc.getLocation().directionTo(enemies[i].location));
						}
					}
					if (attackIndex < 0 && enemies[i].type == RobotType.BIGZOMBIE)
					{
						attackIndex = i;
					}
					if (attackIndex < 0)
					{
						//equalHealth = equalHealth && enemies[i].health == enemies[lowestHealthIndex].health;
						lowestDistanceIndex = rc.getLocation().distanceSquaredTo(enemies[i].location) < 
							rc.getLocation().distanceSquaredTo(enemies[lowestDistanceIndex].location)? 
									i : lowestDistanceIndex;	
						//lowestHealthIndex = enemies[i].health < enemies[lowestHealthIndex].health? i: lowestHealthIndex;
					}
				}
			}
			if(attackIndex < 0)
			{
				//attackIndex = equalHealth? lowestDistanceIndex : lowestHealthIndex;
				attackIndex = lowestDistanceIndex;
			}
			if (attackIndex >= 0 && rc.isWeaponReady())
			{
				rc.attackLocation(enemies[attackIndex].location);
			}
		}
	}
	//TODO Refine this method
	private static boolean moveAwayFromEnemy(RobotController rc, Direction enemyDirection) 
	{
		boolean moved = false;
		try
		{
		Direction moveTo = oppositeDirection(enemyDirection);
		if (rc.isCoreReady()) moveTowards(rc, moveTo);
		} catch (GameActionException e) 
		{
			e.printStackTrace();
		}
		return moved;
	
	}
	private static void moveAwayFromEnemies(RobotController rc) throws GameActionException 
	{
		// TODO might want a different value for how far you sense enemies and 
		// do a "center of mass" of enemies weighted by their attack values then
		// move away from that?
		RobotInfo[] enemies = rc.senseNearbyRobots(rc.getType().sensorRadiusSquared, myTeam.opponent());
		if (enemies.length > 0) {
			Direction awayFrom = rc.getLocation().directionTo(enemies[0].location).opposite();
			if (rc.canMove(awayFrom) && rc.isCoreReady())
			{
				rc.move(awayFrom);
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
		RobotInfo[] allies = rc.senseNearbyRobots(rc.getType().attackRadiusSquared, myTeam);
		for (int i = 0; i < allies.length; i++) {
			if (allies[i].health < allies[i].maxHealth && !allies[i].type.equals(RobotType.ARCHON)) { // can't repair other archons
				rc.repair(allies[i].location);
				break;
			}
		}
	}
	
	/**
	 * Repairs the first-sensed neutral robot, if rc's core is ready
	 * @param rc must be an archon
	 * @throws GameActionException 
	 */
	private static void activateFirst(RobotController rc) throws GameActionException {
		if (!rc.isCoreReady()) return;
		RobotInfo[] nearbyNeutralRobots = rc.senseNearbyRobots(ONE_SQUARE_RADIUS, Team.NEUTRAL);
		if (nearbyNeutralRobots.length > 0) {
			rc.activate(nearbyNeutralRobots[0].location);
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
	private static void moveTowards(RobotController rc, Direction dir) throws GameActionException {
		if (rc.isCoreReady()) 
		{
			Direction[] nearDirections = {dir, dir.rotateRight(), dir.rotateLeft(),
					dir.rotateRight().rotateRight(), dir.rotateLeft().rotateLeft(), 
					dir.rotateRight().rotateRight().rotateRight(), dir.rotateLeft().rotateLeft().rotateLeft()};
			for (Direction nearDir : nearDirections) 
			{
				if (rc.canMove(nearDir) && rc.isCoreReady())
				{
					rc.move(nearDir);
				}
			}
			if (rc.isCoreReady() && rc.onTheMap(rc.getLocation().add(dir)) && rc.senseRubble(rc.getLocation().add(dir)) > RUBBLE_LOWER_CLEAR_THRESHOLD) 
			{
				rc.clearRubble(dir);
			}
		}
	}
	
	/**
	 * 
	 * @param rc
	 * @return true if rc moves else false
	 * @throws GameActionException
	 */
	private static void moveTowardsNearestEnemy (RobotController rc) throws GameActionException {
		List<RobotInfo> enemies = Arrays.asList(rc.senseHostileRobots(rc.getLocation(), -1));
		Optional<RobotInfo> nearestEnemy = enemies.stream().min((enemy1, enemy2) ->
		rc.getLocation().distanceSquaredTo(enemy1.location) - rc.getLocation().distanceSquaredTo(enemy2.location));
		if (nearestEnemy.isPresent()) {
			moveTowards(rc, rc.getLocation().directionTo(nearestEnemy.get().location));
		}
	}
	
	//TODO this method needs a lot of work...isn't doing things properly even if there's just an 
	// archon making bots with open space around it. Add onto that later, when there are more bots around,
	// others will need to move forward in order for those just next to the archon to move. 
	private static boolean moveAwayFromArchons (RobotController rc) throws GameActionException 
	{
		if (!rc.isCoreReady()) return false;
		double archonReserveDistance = Math.sqrt(rc.getRobotCount())*2; //todo make this more finessed 
		List<RobotInfo> robots = Arrays.asList(rc.senseNearbyRobots((int)archonReserveDistance+1, myTeam));
		List<RobotInfo> archons = new ArrayList<>();
		List<Direction> validDirections = new ArrayList<>();
		//for (int i = 0; i<DIRECTIONS.length; i++)
		//{
		//	validDirections.add(DIRECTIONS[i]);
		//}
		for (RobotInfo robot : robots) {
			if (robot.type == RobotType.ARCHON) {
				archons.add(robot);
			}
		}
		boolean tooClose = false;
		MapLocation myLocation = rc.getLocation();
		for (RobotInfo archon : archons) {
			if (myLocation.distanceSquaredTo(archon.location) < archonReserveDistance) {
				tooClose = true;
				break;
			}
		}
		
		if (!tooClose) 
		{
			return false;
		}
		//for (int i=0; i<validDirections.size();)
		//{
		//	for(RobotInfo arch: archons)
		//	{
		//		if(validDirections.get(i) == rc.getLocation().directionTo(arch.location))
		//		{
		//			validDirections.remove(i);
		//			if (i < validDirections.size())
		//			{
		//				validDirections.remove(i);
		//			}
		//			if (i-1 < validDirections.size() && validDirections.size()>0)
		//			{
		//				if(i-1>0)
		//				{
		//					validDirections.remove(i-1);
		//				}
		//				else
		//				{
	    //				validDirections.remove(validDirections.size()-1);
		//				}
		//			}
		//		}
		//		else
		//		{
		//			i++;
		//		}
		//	}
		//}
		//if(validDirections.size()>0)
		//{
		//	for (int i=0; i<validDirections.size(); i++)
		//	{
		//		if(rc.canMove(validDirections.get(i)))
		//		{
		//			return moveTowards(rc, validDirections.get(i));
		//		}
		//	}
		//}
		//
		List<MapLocation> possibleMoveLocations = Arrays.asList(MapLocation.getAllMapLocationsWithinRadiusSq(rc.getLocation(), 2));
		List<MapLocation> goodMoveLocations = new ArrayList<>();
		
		for (MapLocation loc : possibleMoveLocations) {
			boolean goodLocation = true;
			for (RobotInfo archon : archons) {
				if (loc.distanceSquaredTo(archon.location) < archonReserveDistance) {
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
		for(int i=0; i<DIRECTIONS.length; i++)
		{
			if(rc.onTheMap(rc.getLocation().add(DIRECTIONS[i])))
			{
				moveTowards(rc, DIRECTIONS[i]);
			}
		}
		return false;
	}
	
	/**
	 * Moves rc in direction dir if possible, while attempting to stay out of attack range
	 * of hostile robots.
	 * @param rc
	 * @param dir
	 * @throws GameActionException 
	 */
	private static void moveCautiously(RobotController rc, Direction dir) throws GameActionException {
		if (!rc.isCoreReady()) return;
		final MapLocation myLocation = rc.getLocation();
		final Map<MapLocation, Double> moveLocationsToWeights = new HashMap<>();
		for (MapLocation loc : MapLocation.getAllMapLocationsWithinRadiusSq(rc.getLocation(), ONE_SQUARE_RADIUS)) {
			if (loc.equals(rc.getLocation())) {
				continue;
			}
			if (!rc.canMove(myLocation.directionTo(loc))) {
				continue;
			}
			moveLocationsToWeights.put(loc, 1.);
		}
		
		if (moveLocationsToWeights.size() == 0) {
			return;
		}
		
		RobotInfo[] enemies = rc.senseHostileRobots(rc.getLocation(), -1);
		for (MapLocation loc : moveLocationsToWeights.keySet()) {
			for (RobotInfo enemy : enemies) {
				if (enemy.weaponDelay < 1 && enemy.type.attackRadiusSquared >= enemy.location.distanceSquaredTo(loc)) {
					moveLocationsToWeights.put(loc, moveLocationsToWeights.get(loc) + enemy.attackPower);
				}
			}
			moveLocationsToWeights.put(loc, moveLocationsToWeights.get(loc)+distanceFromDirection(dir, myLocation.directionTo(loc)));			
		}
		// TODO(remember, though, the enemies will get to move one more time before you do) 
		MapLocation bestLocation = moveLocationsToWeights.keySet().iterator().next();
		Double lowestWeight = moveLocationsToWeights.get(bestLocation);
		for (MapLocation loc : moveLocationsToWeights.keySet()) {
			if (moveLocationsToWeights.get(loc) < lowestWeight) {
				lowestWeight = moveLocationsToWeights.get(loc);
				bestLocation = loc;
			}
		}
		rc.move(myLocation.directionTo(bestLocation));
	}
	
	/**
	 * 
	 * @param desiredDir
	 * @param otherDir
	 * @return Integer.MAX_VALUE if otherDir can't be rotated to desiredDir or... TODO
	 */
	private static double distanceFromDirection(Direction desiredDir, Direction otherDir) {
		double[] weights = {0, 3, 8, 15, 25};
//		double[] weights = {0.75, 0.8, .85, .9, .95};
//		double[] weights = {1, 1.0001, 1.0002, 1.0003, 1.0004};
		Direction leftRotation = otherDir;
		Direction rightRotation = otherDir;
		for (int i = 0; i <= 4; i++) {
			if (desiredDir.equals(leftRotation) || desiredDir.equals(rightRotation)) {
				return weights[i];
			}
			leftRotation = leftRotation.rotateLeft();
			rightRotation = rightRotation.rotateRight();
			
		}
		return Integer.MAX_VALUE;
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
			if (signal.getMessage() != null && signal.getTeam().equals(myTeam)) {
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
		List<MapLocation> archonLocations = Arrays.asList(rc.getInitialArchonLocations(myTeam));
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
	
	private static boolean turretAttack(RobotController rc) throws GameActionException {
		if (!rc.isWeaponReady()) return false;
		// can't attack if enemy is too close so attackFirst throws errors
		RobotInfo[] enemies = rc.senseHostileRobots(rc.getLocation(), rc.getType().attackRadiusSquared);
		for (RobotInfo enemy : enemies) {
			if (rc.canAttackLocation(enemy.location)) {
				rc.attackLocation(enemy.location);
				return true;
			}
		}
		return false;
	}
	
	private static void archonIntroMode(RobotController rc) throws GameActionException 
	{
		processIntroMessageSignals(rc);
		// TODO: what if, after an appropriate number of turns, the scouts haven't reported anything?
		// should we have all of the archons move together in some direction and hope it is towards a corner?
		if (SCOUTED_CORNERS.size() >= 2) {
			List<Direction> crossDirections = Arrays.asList(Direction.NORTH_WEST,
					Direction.NORTH_EAST, Direction.SOUTH_WEST, Direction.SOUTH_EAST);
			if (!(crossDirections.contains(SCOUTED_CORNERS.get(0)
					.directionTo(SCOUTED_CORNERS.get(1))) || SCOUTED_CORNERS.size() > 2)) {
				// TODO if they are not opposite, make a scout and send to one of the remaining corners
			} else {
				MapLocation[] newCorners = {new MapLocation(SCOUTED_CORNERS.get(0).x, SCOUTED_CORNERS.get(1).y),
						new MapLocation(SCOUTED_CORNERS.get(1).x, SCOUTED_CORNERS.get(0).y)};
				for (MapLocation corner : newCorners) {
					if(!SCOUTED_CORNERS.contains(corner)) {
						SCOUTED_CORNERS.add(corner);
					}
				}
				currentMode = TRANSITION_MODE;
				turtleCorner = findBestTurtleCorner(rc);
				for (MapLocation corner : SCOUTED_CORNERS) {
					if (crossDirections.contains(turtleCorner.directionTo(corner))) {
						fullMapRadius = turtleCorner.distanceSquaredTo(corner);
						break;
					}
				}
				// TODO (also broadcast this locally to scouts when you make them? So they can message
				// back more efficiently, if they have to)
				rc.broadcastMessageSignal(SENDING_MODE, TRANSITION_MODE, rc.getType().sensorRadiusSquared);
				rc.broadcastMessageSignal(SENDING_TURTLE_X, turtleCorner.x, rc.getType().sensorRadiusSquared);
				rc.broadcastMessageSignal(SENDING_TURTLE_Y, turtleCorner.y, rc.getType().sensorRadiusSquared);
			}
		}
		// TODO gathering parts (need locations from scouts first...)
		if (rc.getRobotCount() - numArchons < NUM_INTRO_SCOUTS) {
			tryToBuild(rc, RobotType.SCOUT);
			Direction directionToTargetCorner = calculateDirectionToClosestCorner(rc);
			rc.broadcastMessageSignal(SENDING_TARGET_DIRECTION, DIRECTION_TO_SIGNAL.get(directionToTargetCorner), 4);
		} else {
			if (rand.nextDouble() > 1.66666) { // 2:1 soldier:guard ratio //TODO fix guard code
				tryToBuild(rc, RobotType.GUARD);
			} else {
				tryToBuild(rc, RobotType.SOLDIER);
			}
		}
		
	}
	/**
	 * Archons automatically collect parts on their current location. This method checks for nearby parts within a
	 * radius squared distance of 9 and moves towards the nearest parts.
	 * @param rc must be archon
	 * @return whether parts were collected
	 * @throws GameActionException 
	 */
	private static void collectParts(RobotController rc) throws GameActionException 
	{
		int partRadiusSquared = 9; 
		int lowestDistanceIndex = 0;
		MapLocation myLocation = rc.getLocation();
		MapLocation[] parts = rc.sensePartLocations(partRadiusSquared);
		for (int i=0; i < parts.length; i++)
		{
			if (myLocation.distanceSquaredTo(parts[i]) < myLocation.distanceSquaredTo(parts[lowestDistanceIndex]))
			{
				lowestDistanceIndex = i;
			}
			if (myLocation.distanceSquaredTo(parts[i]) == myLocation.distanceSquaredTo(parts[lowestDistanceIndex]))
			{
				lowestDistanceIndex = rc.senseParts(parts[i]) > rc.senseParts(parts[lowestDistanceIndex]) ? 
																						i : lowestDistanceIndex;
			}
		}
		if (rc.isCoreReady() && parts.length > 0)
		{
			moveTowards(rc, myLocation.directionTo(parts[lowestDistanceIndex]));
		}
	}
	/**
	 * Archon checks within a squared radius of 9 for neutral units. If neutral archon, always head toward
	 * and activate. If no neutral archons are detected, activate adjacent units. If no adjacent units,
	 * move towards nearest neutral unit.
	 * @param rc must be archon
	 * @return if activated a unit
	 * @throws GameActionException 
	 */
	private static void activateUnits(RobotController rc) throws GameActionException 
	{
		int senseRobotRadiusSquared = 9; 
		int lowestDistanceIndex = 0;
		MapLocation myLocation = rc.getLocation();
		MapLocation robotLocation;
		RobotInfo[] robots = rc.senseNearbyRobots(senseRobotRadiusSquared, Team.NEUTRAL);
		for (int i=0; i < robots.length; i++)
		{
			robotLocation = robots[i].location;
			if (robots[i].type == RobotType.ARCHON)
			{
				if (myLocation.isAdjacentTo(robotLocation) && rc.isCoreReady())
				{
					rc.activate(robotLocation);
				}
				else
				{
					moveTowards(rc, myLocation.directionTo(robotLocation));
				}
				break;
			}
			if (robots[i].type != RobotType.ARCHON)
			{
				lowestDistanceIndex = myLocation.distanceSquaredTo(robotLocation) 
						< myLocation.distanceSquaredTo(robots[lowestDistanceIndex].location) ? i : lowestDistanceIndex;
			}
		}
		if (rc.isCoreReady() && robots.length > 0)
		{
			if (myLocation.isAdjacentTo(robots[lowestDistanceIndex].location))
			{
				rc.activate(robots[lowestDistanceIndex].location);
			}
			else
			{
				moveTowards(rc, myLocation.directionTo(robots[lowestDistanceIndex].location));
			}
		}
	}
	//smile
}
