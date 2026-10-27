package com.freshorangeslices.bloomlevelup.skill.misc;

/**
 * The XP a player held when they started dying, kept on the dead player (ServerPlayerXpMixin) so XP Insurance
 * counts it on respawn even if a mod took the XP away during the death: Universal Graves sets the level to 0 when it
 * stores the drop in a grave.
 */
public interface XpAtDeath {
	/** The level when the player died, or -1 if they haven't died. */
	int bloomlevelup$levelAtDeath();

	float bloomlevelup$progressAtDeath();
}
