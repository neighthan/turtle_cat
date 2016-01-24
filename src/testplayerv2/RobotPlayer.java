package testplayerv2;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.Signal;
import battlecode.common.Team;
public class RobotPlayer 
{
	// Strategic stages
		private static boolean introMode = true;
		private static boolean transitionMode = false;
		private static boolean turtleMode = false;
		
		private static MapLocation turtleCorner;
//		private final static int PARTS_THRESHOLD = 100;
		private static final int RUBBLE_LOWER_CLEAR_THRESHOLD = 40;
		private static final Direction[] DIRECTIONS = {Direction.NORTH, Direction.NORTH_EAST, Direction.EAST,
				Direction.SOUTH_EAST, Direction.SOUTH, Direction.SOUTH_WEST, Direction.WEST, Direction.NORTH_WEST};
		private static final Direction[] OPPOSITE_DIRECTIONS = {Direction.SOUTH, Direction.SOUTH_WEST, Direction.WEST, 
					Direction.NORTH_WEST,Direction.NORTH, Direction.NORTH_EAST, Direction.EAST, Direction.SOUTH_EAST};
		private static Random rand;
		
		private final static int NUM_INTRO_SCOUTS = 4;
		private static final int ARCHON_RESERVED_DISTANCE_SQUARED = 10;
		private static final int ONE_SQUARE_RADIUS = 2;
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
		
		/**
		 * @param rc
		 */
		public static void run(RobotController rc) 
		{
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
		 * @Nathan - Should we adjust these priorities (specifically where should building troops be?)
		 * TODO for archons - 
		 * 1) repair, always run away from enemies (excluding scouts)
		 * 2) signal location
		 * 3) pick up adjacent parts and activate neutral units
		 * 4) move to turtle corner
		 * 5) build troops
		 * @param rc
		 * @throws GameActionException
		 */
		private static void archon(RobotController rc)
		{
			numArchons = rc.getInitialArchonLocations(rc.getTeam()).length;
			while (true){
			try {
				// 1) repair nearest injured unit
				// if senses enemy troop that is not a scout, run away from it
			repairFirst(rc);
			boolean moved =  moveAwayFromEnemies(rc);
				// 2) signal location - only if all intro scouts have been built (guaranteed by turn 90)
			if (rc.getRoundNum() > 90)
			{
				// @Hope - fix broadcast signal range - make it calculated based on number of troops
				rc.broadcastSignal(rc.getType().sensorRadiusSquared*2);
			}
				// 3) Pick up parts on current square, check for adjacent parts and try to collect them
			if (!moved && rc.isCoreReady()) moved = collectParts(rc);
				// 3a) activate any nearby units
			if (!moved && rc.isCoreReady()) moved = activateUnits(rc);
			
			// @Nathan - Intro mode is all you -- I just pasted all the text into a separate method for clarity
			if (introMode) 
			{
				archonIntroMode(rc);
			}
			
			// 4) Move to Turtle Corner
			if (transitionMode && !moved && rc.isCoreReady()) 
			{
				//transition
				if (rc.getLocation().distanceSquaredTo(turtleCorner) > 4) 
				{
					moved = moveTowards(rc, rc.getLocation().directionTo(turtleCorner));
				} 
				else 
				{
						// TODO get in proper position
						transitionMode = false;
						turtleMode = true;
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
			}
			catch (GameActionException e) 
			{
				e.printStackTrace();
			}	
			}
			}


		private static int turnsToCheckForZombies = 0; // @Nathan what is this for? 
		//@Nathan - scouts method is all you
		private static void scout(RobotController rc) 
		{
			// do one time things here
			Direction targetDirection = Direction.NONE;
			while (true) {
			try {
				// TODO - I've made it so the scouts will go in pairs now; not sure if we want this later or not, but they
				// aren't avoiding enemies well enough
				if (introMode && rc.getRoundNum() >= 40) 
				{
					// TODO - scouts don't pick their directions well enough - try running diffusion.
					// one often starts already in/near a corner; if we find a corner very close, we should probably just move to it
					// and turtle right away (even if we don't know about the others yet)
					if (targetDirection.equals(Direction.NONE)) 
					{
						targetDirection = getScoutTargetDirection(rc);
					}
					
					//RobotInfo[] zombies = rc.senseNearbyRobots(-1, Team.ZOMBIE);
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
						
					MapLocation corner = checkForCorner(rc, targetDirection);
					if (corner != null) {
						rc.broadcastMessageSignal(SENDING_CORNER_X, corner.x, fullMapRadius);
						rc.broadcastMessageSignal(SENDING_CORNER_Y, corner.y, fullMapRadius);
						// head to opposite in case other scout didn't make it
						// TODO or scout around for other zombie dens, kite, etc.
						targetDirection = targetDirection.opposite();
					}
					moveCautiously(rc, targetDirection);
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
//					rc.sensePartLocations(-1); //TODO
					}
			}
			catch (GameActionException e) 
			{
				e.printStackTrace();
			}	
			}
		}
		/**
		 * As long as we aren't actively making guards just this simple code should be enough.
		 * First try to attack. Then move towards the nearest enemy;
		 * @param rc
		 */
		private static void guard(RobotController rc) 
		{
		// do one time things here
		while (true) 
		{
			try {
				//TODO smarter attacking (make a method to pick and attack, move if appropriate)
				boolean attacked = attackFirst(rc);
				if (!attacked && rc.isCoreReady() && rc.isWeaponReady()) 
				{
					//so that you don't try to move and get delay if you should be getting ready to attack
					moveTowardsNearestEnemy(rc);
				}
				}				
				catch (GameActionException e) 
				{
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
						//TODO add in signaling!!!
						//if (rc.isCoreReady()) processSignals(rc); // 3)
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
		//TODO FIX THIS METHOD UP WITH SIGNALS YO
		private static void moveTowardsArchon(RobotController rc) throws GameActionException
		{
			RobotInfo[] nearbyRobots =  rc.senseNearbyRobots();
			int moveRadius = 9; // @Hope Calculate based on num troops
			for (int i=0; i<nearbyRobots.length; i++)
			{
				if(nearbyRobots[i].type == RobotType.ARCHON)
				{
					Direction dir = rc.getLocation().directionTo(nearbyRobots[i].location);
					if (nearbyRobots[i].location.distanceSquaredTo(rc.getLocation()) >= moveRadius)
					{
						if(rc.canMove(dir) && rc.isCoreReady())
						{
							moveTowards(rc, dir);
						} 
					}
				}
			}
		
	    }

		private static void turret(RobotController rc) {
			// do one time things here
			while (true) {
//				try {
					Clock.yield();				
//				} catch (GameActionException e) {
//					e.printStackTrace();
//				}
			}
		}

		private static void ttm(RobotController rc) {
			// do one time things here
			while (true) {
//				try {
					Clock.yield();				
//				} catch (GameActionException e) {
//					e.printStackTrace();
//				}
			}
		}

		private static void viper(RobotController rc) {
			// in case we use vipers later
			// do one time things here
			while (true) {
//				try {
					Clock.yield();				
//				} catch (GameActionException e) {
//					e.printStackTrace();
//				}
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
		 * @param RobotController 
		 * @throws GameActionException
		 * @return true if this robot attacked else false
		 */
		private static boolean attackFirst(RobotController rc) throws GameActionException 
		{
			boolean hasAttacked = false;
			//boolean equalHealth = true;
			//int lowestHealthIndex = 0;
			int lowestDistanceIndex = 0;
			int attackIndex = -1000000;
			boolean moved = false;
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
							//TODO test this part
							if ((enemies[i].type == RobotType.BIGZOMBIE || enemies[i].type == RobotType.STANDARDZOMBIE)
									&& rc.getRoundNum()%2 == 0)
							{
								moved = moveAwayFromEnemy(rc, rc.getLocation().directionTo(enemies[i].location));
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
								rc.getLocation().distanceSquaredTo(enemies[lowestDistanceIndex].location)? i : lowestDistanceIndex;	
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
					hasAttacked = true;
				}
			}
			return hasAttacked;
		}
		//TODO Refine this method
		private static boolean moveAwayFromEnemy(RobotController rc, Direction enemyDirection) 
		{
			boolean moved = false;
			try{
			int tryToMoveIndex=0;
			for (int i=0; i < DIRECTIONS.length; i++)
			{
				if (DIRECTIONS[i] == enemyDirection)
				{
					tryToMoveIndex = i;
				}
			}
			moved = moveTowards(rc, OPPOSITE_DIRECTIONS[tryToMoveIndex]);
			} catch (GameActionException e) 
			{
				e.printStackTrace();
			}
			return moved;
		
		}
		//TODO This should be the archon running away method...
		private static boolean moveAwayFromEnemies(RobotController rc) 
		{
			boolean moved = false;
			// TODO move in opposite direction of enemies
			return moved;
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
		private static boolean moveTowards(RobotController rc, Direction dir) throws GameActionException 
		{
			if (rc.isCoreReady()) 
			{
				int lowestRubbleIndex = 0;
				Direction[] nearDirections = {dir, dir.rotateRight(), dir.rotateLeft(),
						dir.rotateRight().rotateRight(), dir.rotateLeft().rotateLeft(),
						dir.rotateRight().rotateRight().rotateRight(), dir.rotateLeft().rotateLeft().rotateLeft()};
				for (Direction nearDir : nearDirections) 
				{
					if (rc.canMove(nearDir)) 
					{
						rc.move(nearDir);
						return true;
					}
				}
				//TODO - test this part I added - it checks for the lowest rubble amount and then tries to clear it
				for (int i=0; i<nearDirections.length; i++)
				{
					lowestRubbleIndex = rc.onTheMap(rc.getLocation().add(nearDirections[i]))
							&& rc.senseRubble(rc.getLocation().add(nearDirections[i])) < 
							rc.senseRubble(rc.getLocation().add(nearDirections[lowestRubbleIndex]))? i : lowestRubbleIndex; 
				}
				if (rc.onTheMap(rc.getLocation().add(dir)) && rc.senseRubble(rc.getLocation().add(dir)) > RUBBLE_LOWER_CLEAR_THRESHOLD) 
				{
					rc.clearRubble(nearDirections[lowestRubbleIndex]);
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
		private static boolean moveTowardsNearestEnemy (RobotController rc) throws GameActionException 
		{
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
		private static boolean moveAwayFromArchons (RobotController rc) throws GameActionException 
		{
			double archonReserveDistance = Math.pow(rc.getRobotCount()/7, 2)+2; //todo make this more finessed 
			List<RobotInfo> robots = Arrays.asList(rc.senseNearbyRobots((int)archonReserveDistance+1, rc.getTeam()));
			List<RobotInfo> archons = new ArrayList<>();
			boolean moved = false;
			for (RobotInfo robot : robots) 
			{
				if (robot.type == RobotType.ARCHON) 
				{
					archons.add(robot);
				}
			}
			boolean tooClose = false;
			MapLocation myLocation = rc.getLocation();
			for (RobotInfo archon : archons) 
			{
				if (myLocation.distanceSquaredTo(archon.location) < archonReserveDistance) 
				{
					tooClose = true;
					break;
				}
			}
			
			if (!tooClose) 
			{
				return false;
			}
			
			List<MapLocation> possibleMoveLocations = Arrays.asList(MapLocation.getAllMapLocationsWithinRadiusSq(rc.getLocation(), 2));
			List<MapLocation> goodMoveLocations = new ArrayList<>();
			
			for (MapLocation loc : possibleMoveLocations) 
			{
				boolean goodLocation = true;
				for (RobotInfo archon : archons) 
				{
					if (loc.distanceSquaredTo(archon.location) < archonReserveDistance || !rc.onTheMap(loc)) 
					{
						goodLocation = false;
						break;
					}
				}
				if (goodLocation) 
				{
					goodMoveLocations.add(loc);
				}
			}
			
			for (MapLocation loc : goodMoveLocations) 
			{
				if (!moved)
				{
					moved = moveTowards(rc, rc.getLocation().directionTo(loc));
				}
			}
			for(int i=0; i<DIRECTIONS.length; i++)
			{
				if(!moved && rc.onTheMap(rc.getLocation().add(DIRECTIONS[i])))
				{
					moved = moveTowards(rc, DIRECTIONS[i]);
				}
			}
			return moved;
		}
		
		/**
		 * Moves rc in direction dir if possible, while attempting to stay out of attack range
		 * of hostile robots.
		 * @param rc
		 * @param dir
		 * @throws GameActionException 
		 */
		private static void moveCautiously(RobotController rc, Direction dir) throws GameActionException 
		{
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
				moveLocationsToWeights.put(loc, moveLocationsToWeights.get(loc)*distanceFromDirection(dir, myLocation.directionTo(loc)));			
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
		private static double distanceFromDirection(Direction desiredDir, Direction otherDir) 
		{
//			double[] weights = {0.75, 0.85, 1, 1.2, 1.4};
			double[] weights = {0.75, 0.8, .85, .9, .95};
//			double[] weights = {1, 1.0001, 1.0002, 1.0003, 1.0004};
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
		 * Archon checks within a squared radius of 9 for neutral units. If neutral archon, always head toward
		 * and activate. If no neutral archons are detected, activate adjacent units. If no adjacent units,
		 * move towards nearest neutral unit.
		 * @param rc must be archon
		 * @return if activated a unit
		 * @throws GameActionException 
		 */
		private static boolean activateUnits(RobotController rc) throws GameActionException 
		{
			boolean hasActivated = false;
			boolean hasMoved = false;
			int senseRobotRadiusSquared = 9; 
			int lowestDistanceIndex = 0;
			MapLocation myLocation = rc.getLocation();
			MapLocation robotLocation;
			RobotInfo[] robots = rc.senseNearbyRobots(senseRobotRadiusSquared, Team.NEUTRAL);
			for (int i=0; i < robots.length; i++)
			{
				robotLocation = robots[i].location;
				if (robots[i].type == RobotType.ARCHON && !hasActivated)
				{
					if (myLocation.isAdjacentTo(robotLocation) && rc.isCoreReady())
					{
						rc.activate(robotLocation);
						hasActivated = true;
					}
					else
					{
						hasMoved = moveTowards(rc, myLocation.directionTo(robotLocation));
					}
				}
				if (robots[i].type != RobotType.ARCHON && !hasActivated)
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
					hasActivated = true;
				}
				else
				{
					hasMoved = moveTowards(rc, myLocation.directionTo(robots[lowestDistanceIndex].location));
				}
			}
			
			return hasActivated;
		}
		/**
		 * Archons automatically collect parts on their current location. This method checks for nearby parts within a
		 * radius squared distance of 9 and moves towards the nearest parts.
		 * @param rc must be archon
		 * @return whether parts were collected
		 * @throws GameActionException 
		 */
		private static boolean collectParts(RobotController rc) throws GameActionException 
		{
			boolean hasMoved = false;
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
				hasMoved = moveTowards(rc, myLocation.directionTo(parts[lowestDistanceIndex]));
			}
			
			return hasMoved;
		}
		
		private static void archonIntroMode(RobotController rc) 
		{
			processIntroMessageSignals(rc);
			// TODO: what if, after an appropriate number of turns, the scouts haven't reported anything?
			// should we have all of the archons move together in some direction and hope it is towards a corner?
			if (CORNERS_ALREADY_SCOUTED.size() >= 2) 
			{
				List<Direction> crossDirections = Arrays.asList(Direction.NORTH_WEST,
				Direction.NORTH_EAST, Direction.SOUTH_WEST, Direction.SOUTH_EAST);
				if (!(crossDirections.contains(CORNERS_ALREADY_SCOUTED.get(0)
						.directionTo(CORNERS_ALREADY_SCOUTED.get(1))) || CORNERS_ALREADY_SCOUTED.size() > 2)) 
				{
					// TODO if they are not opposite, make a scout and send to one of the remaining corners
				} 
				else 
				{
					MapLocation[] newCorners = {new MapLocation(CORNERS_ALREADY_SCOUTED.get(0).x, CORNERS_ALREADY_SCOUTED.get(1).y),
							new MapLocation(CORNERS_ALREADY_SCOUTED.get(1).x, CORNERS_ALREADY_SCOUTED.get(0).y)};
					for (MapLocation corner : newCorners)
					{
						if(!CORNERS_ALREADY_SCOUTED.contains(corner))
						{
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
			
		}
		
		/**
		 * @return a random direction from DIRECTIONS
		 */
		private static Direction getRandomDirection() 
		{
			return DIRECTIONS[(int) rand.nextDouble()*9];
		}
}
