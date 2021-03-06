package mffs.field

import java.util
import java.util.Optional

import mffs.api.machine.ForceField
import mffs.api.modules.Module
import mffs.content.{Content, Textures}
import mffs.security.MFFSPermissions
import mffs.util.MFFSUtility
import nova.core.block.Block
import nova.core.block.components.{LightEmitter, StaticRenderer}
import nova.core.entity.Entity
import nova.core.entity.component.Damageable
import nova.core.game.Game
import nova.core.item.Item
import nova.core.network.{PacketHandler, Sync}
import nova.core.player.Player
import nova.core.render.model.{BlockModelUtil, Model}
import nova.core.render.texture.Texture
import nova.core.retention.{Storable, Stored}
import nova.core.util.Direction
import nova.core.util.transform.shape.Cuboid
import nova.core.util.transform.vector.Vector3i

import scala.collection.convert.wrapAll._

class BlockForceField extends Block with PacketHandler with ForceField with LightEmitter with Storable with StaticRenderer {

	@Stored
	@Sync
	private var camoBlock: Block = null
	@Stored
	@Sync
	private var projector: Vector3i = null

	/**
	 * Constructor
	 */
	override def getHardness: Double = Double.PositiveInfinity

	override def getResistance: Double = Double.PositiveInfinity

	override def isCube: Boolean = false

	override def getDrops: util.Set[Item] = Set.empty[Item]

	/**
	 * Rendering
	 */
	override def renderStatic(model: Model) {
		//TODO: Render pass?
		camoBlock match {
			case block: StaticRenderer => block.renderStatic(model)
			case _ => BlockModelUtil.drawBlock(model, this)
		}
	}

	override def shouldRenderSide(side: Direction): Boolean = {
		if (camoBlock != null) {
			try {
				return camoBlock.shouldRenderSide(side)
			}
			catch {
				case e: Exception =>
					e.printStackTrace()
			}
			return true
		}

		val block = world.getBlock(position + side.toVector)
		return if (block.isPresent) sameType(block.get()) else true
	}

	override def getCollidingBoxes(intersect: Cuboid, entity: Optional[Entity]): util.Set[Cuboid] = {
		val projector = getProjector()

		if (projector != null && entity.isPresent && entity.get.isInstanceOf[Player]) {
			val biometricIdentifier = projector.getBiometricIdentifier
			val entityPlayer = entity.get.asInstanceOf[Player]

			if (biometricIdentifier != null) {
				if (biometricIdentifier.hasPermission(entityPlayer.getID, MFFSPermissions.forceFieldWarp)) {
					return null
				}
			}

		}

		super.getCollidingBoxes(intersect, entity)
	}

	/**
	 * @return Gets the projector block controlling this force field. Removes the force field if no
	 *         projector can be found.
	 */
	def getProjector: BlockProjector = {
		if (this.getProjectorSafe != null) {
			return getProjectorSafe
		}

		if (Game.instance.networkManager.isServer) {
			world.removeBlock(position)
		}

		return null
	}

	def getProjectorSafe: BlockProjector = {
		if (projector != null) {

			val projBlock = world.getBlock(projector)
			if (projBlock.isPresent) {
				val proj = projBlock.get().asInstanceOf[BlockProjector]
				if (Game.instance.networkManager.isClient || (proj.getCalculatedField != null && proj.getCalculatedField.contains(position))) {
					return proj
				}
			}
		}

		return null
	}

	def setProjector(position: Vector3i) {
		projector = position

		if (Game.instance.networkManager.isServer) {
			refreshCamoBlock()
		}
	}

	def refreshCamoBlock() {
		if (getProjectorSafe != null) {
			camoBlock = MFFSUtility.getCamoBlock(getProjector, position).getDummy
		}
	}

	override def onEntityCollide(entity: Entity) {
		val projector = getProjector()

		if (projector != null) {
			if (!projector.getModules().forall(stack => stack.asInstanceOf[Module].onFieldCollide(this, entity))) {
				return
			}

			val biometricIdentifier = projector.getBiometricIdentifier

			if ((position().toDouble + 0.5).distance(entity.position()) < 0.5) {
				if (Game.instance.networkManager.isServer && entity.isInstanceOf[Damageable]) {
					val entityLiving = entity.asInstanceOf[Damageable]

					//					entityLiving.addPotionEffect(new PotionEffect(Potion.confusion.id, 4 * 20, 3))
					//					entityLiving.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 20, 1))

					if (entity.isInstanceOf[Player]) {
						val player = entity.asInstanceOf[Player]

						if (biometricIdentifier != null) {
							if (biometricIdentifier.hasPermission(player.getID, MFFSPermissions.forceFieldWarp)) {
								return
							}
						}
					}
				}
			}
		}

	}

	override def getTexture(side: Direction): Optional[Texture] = Optional.of(Textures.forceField)

	override def getEmittedLightLevel: Float = {
		val projector = getProjectorSafe
		if (projector != null) {
			return Math.min(projector.getModuleCount(Content.moduleGlow), 64) / 64f
		}

		return 0
	}

	override def getID: String = "forceField"

	override def weakenForceField(energy: Int) {
		val projector = getProjector

		if (projector != null) {
			projector.addFortron(energy, true)
		}

		if (Game.instance.networkManager.isServer) {
			world.removeBlock(position)
		}
	}
}