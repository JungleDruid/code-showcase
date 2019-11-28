/// <summary>
/// 此為近期研究Unity ECS系統時開發之遊戲的單位生成程式碼。
/// Unity ECS系統目前還在preview階段，官方預計於2020年正式發佈。
/// 此系統將能大幅提升遊戲效能和解決目前Unity許多開發上的瓶頸。
/// </summary>

// 一個用來決定是否生成單位的Component Tag
public struct PendingSpawn : IComponentData { }

// 存放單位血量的Component
public struct Health : IComponentData
{
    public float Value;
}

// 存放單位初始值的Component
public struct UnitInitialization : IComponentData
{
    public int MaxHealth;
    public Color Color;
    public int WeaponIndex;
}

// 單位生成器的Component
public class UnitSpawnerComponent : MonoBehaviour
{
    public SkeletonAnimation Prefab;
    public bool FlipX;
}

// 掌管單位生成的系統，將單位放置於生成器的位置，並決定是否翻轉單位。
[UpdateAfter(typeof(UnitInitializationSystem))] // 將此系統排程在單位初始化後，主要是為了解決Spine尚未ECS化所造成的視覺bug
public class UnitSpawnSystem : ComponentSystem
{
    protected override void OnUpdate()
    {
        Entities.WithAll<PendingSpawn>().ForEach((Entity entity, UnitSpawnerComponent spawner) =>
        {
            if (spawner.Prefab == null) return;

            var go = Object.Instantiate(spawner.Prefab, spawner.transform.position, Quaternion.identity);
            if (spawner.FlipX)
            {
                go.Skeleton.ScaleX *= -1;
            }
            go.GetComponent<Renderer>().enabled = false;

            EntityManager.RemoveComponent<PendingSpawn>(entity);
        });
    }
}

// 掌管單位初始化的系統，依照UnitInitialization Component的設定值來決定單位的血量、顏色和武器
public class UnitInitializationSystem : ComponentSystem
{
    private Attachment[] _attachments;

    protected override void OnCreate()
    {
        _attachments = World.GetExistingSystem<DataSystem>().GetWeaponAttachments();
    }

    protected override void OnUpdate()
    {
        Entities.ForEach((Entity entity, SkeletonAnimation animation, ref UnitInitialization initialization, ref Health health) =>
        {
            health.Value = initialization.MaxHealth;
            foreach (var slot in animation.Skeleton.Slots)
            {
                switch (slot.Data.Name)
                {
                    case "MainWeapon":
                        slot.Attachment = _attachments[initialization.WeaponIndex];
                        break;
                    default:
                        slot.SetColor(initialization.Color);
                        break;
                }
            }
            animation.GetComponent<Renderer>().enabled = true;

            EntityManager.RemoveComponent<UnitInitialization>(entity);
        });
    }
}
