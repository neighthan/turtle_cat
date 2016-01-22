package testplayer;

import battlecode.common.*;

import java.util.Random;

public class RobotPlayer {

	// You can instantiate variables here.
	private static final Direction[] DIRECTIONS = {Direction.NORTH, Direction.NORTH_EAST, Direction.EAST, Direction.SOUTH_EAST,
			Direction.SOUTH, Direction.SOUTH_WEST, Direction.WEST, Direction.NORTH_WEST};
	private static final RobotType[] ROBOT_TYPES = {RobotType.SCOUT, RobotType.SOLDIER, RobotType.SOLDIER, RobotType.SOLDIER,
			RobotType.GUARD, RobotType.GUARD, RobotType.VIPER, RobotType.TURRET};
	
    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * If this method returns, the robot dies!
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) {
        Random rand = new Random(rc.getID());
        Team myTeam = rc.getTeam();
        Team enemyTeam = myTeam.opponent();

        if (rc.getType() == RobotType.ARCHON) {
            archon(rc, rand);
        } else if (rc.getType() != RobotType.TURRET) {
            notTurret(rc, rand, enemyTeam);
        } else if (rc.getType() == RobotType.TURRET) {
            turret(rc, enemyTeam);
        }
    }

	private static void turret(RobotController rc, Team enemyTeam) {
		final int myAttackRange = rc.getType().attackRadiusSquared;
		try {
			
		} catch (Exception e) {
		    System.out.println(e.getMessage());
		    e.printStackTrace();
		}

		while (true) {
		    // This is a loop to prevent the run() method from returning. Because of the Clock.yield()
		    // at the end of it, the loop will iterate once per game round.
		    try {
		        // If this robot type can attack, check for enemies within range and attack one
		        if (rc.isWeaponReady()) {
		            RobotInfo[] enemiesWithinRange = rc.senseNearbyRobots(myAttackRange, enemyTeam);
		            RobotInfo[] zombiesWithinRange = rc.senseNearbyRobots(myAttackRange, Team.ZOMBIE);
		            if (enemiesWithinRange.length > 0) {
		                for (RobotInfo enemy : enemiesWithinRange) {
		                    // Check whether the enemy is in a valid attack range (turrets have a minimum range)
		                    if (rc.canAttackLocation(enemy.location)) {
		                        rc.attackLocation(enemy.location);
		                        break;
		                    }
		                }
		            } else if (zombiesWithinRange.length > 0) {
		                for (RobotInfo zombie : zombiesWithinRange) {
		                    if (rc.canAttackLocation(zombie.location)) {
		                        rc.attackLocation(zombie.location);
		                        break;
		                    }
		                }
		            }
		        }

		        Clock.yield();
		    } catch (Exception e) {
		        System.out.println(e.getMessage());
		        e.printStackTrace();
		    }
		}
	}

	private static void notTurret(RobotController rc, Random rand, Team enemyTeam) {
		final int myAttackRange = rc.getType().attackRadiusSquared;
		try {
		    // Any code here gets executed exactly once at the beginning of the game.
		} catch (Exception e) {
		    // Throwing an uncaught exception makes the robot die, so we need to catch exceptions.
		    // Caught exceptions will result in a bytecode penalty.
		    System.out.println(e.getMessage());
		    e.printStackTrace();
		}

		while (true) {
		    // This is a loop to prevent the run() method from returning. Because of the Clock.yield()
		    // at the end of it, the loop will iterate once per game round.
		    try {
		        boolean shouldAttack = false;

		        // If this robot type can attack, check for enemies within range and attack one
		        RobotInfo[] enemiesWithinRange = rc.senseHostileRobots(rc.getLocation(), myAttackRange);
		        if (myAttackRange > 0) {
		            if (enemiesWithinRange.length > 0) {
		                shouldAttack = true;
		                // Check if weapon is ready
		                if (rc.isWeaponReady()) {
		                    rc.attackLocation(enemiesWithinRange[rand.nextInt(enemiesWithinRange.length)].location);
		                }
		            }
		        }

		        final Direction dirToMove;
		        if (shouldAttack && rc.isCoreReady()) {
		        	// Move towards nearest enemy
		        	MapLocation enemyLocation = enemiesWithinRange[0].location;
		        	dirToMove = rc.getLocation().directionTo(enemyLocation);
		        }
		        else {
		        	dirToMove = Direction.SOUTH_EAST;
		        }
		        // Check the rubble in that direction
		        if (rc.senseRubble(rc.getLocation().add(dirToMove)) >= GameConstants.RUBBLE_OBSTRUCTION_THRESH && rc.isCoreReady()) {
		        	// Too much rubble, so I should clear it
		        	rc.clearRubble(dirToMove);
		        	// Check if I can move in this direction
		        } else {
		        	moveTowards(rc, dirToMove);
		        }
		        Clock.yield();
		    } catch (Exception e) {
		        System.out.println(e.getMessage());
		        e.printStackTrace();
		    }
		}
	}

	private static void archon(RobotController rc, Random rand) {
		try {
		    // Any code here gets executed exactly once at the beginning of the game.
		} catch (Exception e) {
		    // Throwing an uncaught exception makes the robot die, so we need to catch exceptions.
		    // Caught exceptions will result in a bytecode penalty.
		    System.out.println(e.getMessage());
		    e.printStackTrace();
		}

		while (true) {
		    // This is a loop to prevent the run() method from returning. Because of the Clock.yield()
		    // at the end of it, the loop will iterate once per game round.
		    try {
		        int fate = rand.nextInt(1000);
		        if (rc.isCoreReady()) {
		            if (fate < 500) {
		                // Choose a random direction to try to move in
		                Direction dirToMove = DIRECTIONS[fate % 8];
		                // Check the rubble in that direction
		                if (rc.senseRubble(rc.getLocation().add(dirToMove)) >= GameConstants.RUBBLE_OBSTRUCTION_THRESH) {
		                    // Too much rubble, so I should clear it
		                    rc.clearRubble(dirToMove);
		                // Check if I can move in this direction
		                } else {
		                    moveTowards(rc, dirToMove);
		                }
		            } else {
		                // Choose a random unit to build
		                RobotType typeToBuild = ROBOT_TYPES[fate % 8];
		                // Check for sufficient parts
		                if (rc.hasBuildRequirements(typeToBuild)) {
		                    // Choose a random direction to try to build in
		                    Direction dirToBuild = DIRECTIONS[rand.nextInt(8)];
		                    for (int i = 0; i < 8; i++) {
		                        // If possible, build in this direction
		                        if (rc.canBuild(dirToBuild, typeToBuild)) {
		                            rc.build(dirToBuild, typeToBuild);
		                            break;
		                        } else {
		                            // Rotate the direction to try
		                            dirToBuild = dirToBuild.rotateLeft();
		                        }
		                    }
		                }
		            }
		        }
		        Clock.yield();
		    } catch (Exception e) {
		        System.out.println(e.getMessage());
		        e.printStackTrace();
		    }
		}
	}
	
	private static void moveTowards(RobotController rc, Direction dir) {
		Direction[] nearDirections = {dir, dir.rotateRight(), dir.rotateLeft(),
				dir.rotateRight().rotateRight(), dir.rotateLeft().rotateLeft()};
		boolean moved = false;
		for (Direction nearDir : nearDirections) {
			if (rc.canMove(nearDir) && rc.isCoreReady()) {
				try {
					rc.move(dir);
				} catch (GameActionException e) {
					e.printStackTrace(); // if core not ready
				}
			}
		}
		if (!moved) { //if you get stuck, perhaps allow moving a direction away from where you want to go?
//			rc.move(Direction.values()[]);
		}
	}
}
