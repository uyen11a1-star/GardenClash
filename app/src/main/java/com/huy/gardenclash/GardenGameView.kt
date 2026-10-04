package com.huy.gardenclash

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class GardenGameView(context: Context) : View(context) {
    private enum class Screen { HOME, MODE_SELECT, BATTLE, PAUSE, RESULT, COLLECTION, SETTINGS }

    private enum class PlantType(val cost: Int, val color: Int, val accent: Int, val title: String, val desc: String, val cooldown: Float) {
        SUN_BLOOM(50, Color.rgb(255, 205, 55), Color.rgb(255, 244, 150), "Sun Bloom", "Produces sun", 5f),
        PEA_POD(100, Color.rgb(66, 164, 82), Color.rgb(183, 246, 112), "Pea Pod", "Rapid lane shots", 4f),
        BURST_BERRY(150, Color.rgb(211, 75, 119), Color.rgb(255, 164, 187), "Burst Berry", "Splash damage", 9f),
        WALL_BUD(50, Color.rgb(77, 139, 91), Color.rgb(186, 232, 165), "Wall Bud", "Heavy blocker", 12f)
    }

    private data class Plant(val row:Int,val col:Int,val type:PlantType,var hp:Float,var timer:Float=0f)
    private data class Enemy(var row:Int,var x:Float,val type:Int,var hp:Float,val maxHp:Float,var speed:Float,var attackTimer:Float=0f,var bob:Float=0f)
    private data class Bullet(var row:Int,var x:Float,var damage:Float,var kind:Int=0)
    private data class Particle(var x:Float,var y:Float,var vx:Float,var vy:Float,var life:Float,val color:Int,var size:Float=4f)
    private data class FloatText(var x:Float,var y:Float,var text:String,var life:Float,val color:Int)
    private data class SunDrop(var x:Float,var y:Float,var targetY:Float,var life:Float=7f,var pulse:Float=0f)

    private val bg=Paint(Paint.ANTI_ALIAS_FLAG)
    private val ui=Paint(Paint.ANTI_ALIAS_FLAG)
    private val text=Paint(Paint.ANTI_ALIAS_FLAG).apply{typeface=Typeface.create("sans",Typeface.BOLD)}
    private val normal=Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=2f}
    private val plants=ArrayList<Plant>(); private val enemies=ArrayList<Enemy>(); private val bullets=ArrayList<Bullet>()
    private val particles=ArrayList<Particle>(); private val floatTexts=ArrayList<FloatText>(); private val sunsDrop=ArrayList<SunDrop>()
    private val cooldown=FloatArray(PlantType.entries.size)
    private val rand=Random(1337)
    private var screen=Screen.HOME; private var mode="ADVENTURE"; private var selected=PlantType.PEA_POD
    private var suns=150; private var score=0; private var crowns=0; private var lives=3; private var plantFood=1
    private var wave=1; private var totalWaves=8; private var waveTimer=0f; private var spawnTimer=0f; private var timeAlive=0f
    private var gameOver=false; private var victory=false; private var paused=false; private var shovel=false; private var highGraphics=true
    private var sfx=true; private var music=true; private var vibration=true; private var lastNs=System.nanoTime(); private var flash=0f
    private val rows=5; private val cols=9
    private fun sx()=width/1280f; private fun sy()=height/720f
    private fun X(v:Float)=v*sx(); private fun Y(v:Float)=v*sy()
    private fun cx(c:Int)=285f+c*92f; private fun cy(r:Int)=178f+r*76f

    override fun onAttachedToWindow(){super.onAttachedToWindow();postOnAnimation(loop)}
    private val loop=object:Runnable{override fun run(){val now=System.nanoTime();val dt=min(.05f,(now-lastNs)/1_000_000_000f);lastNs=now;update(dt);invalidate();postOnAnimation(this)}}

    private fun update(dt:Float){
        if(screen!=Screen.BATTLE||gameOver||paused)return
        timeAlive+=dt;spawnTimer+=dt;waveTimer+=dt;flash=max(0f,flash-dt)
        for(i in cooldown.indices)cooldown[i]=max(0f,cooldown[i]-dt)
        if(waveTimer>18f&&wave<totalWaves){wave++;waveTimer=0f;toast(730f,78f,"WAVE $wave",Color.rgb(255,224,100));repeat(2){spawnEnemy()}}
        val spawnGap=max(.65f,2.15f-timeAlive*.012f)
        if(spawnTimer>spawnGap){spawnTimer=0f;spawnEnemy();if(wave>=3&&rand.nextFloat()<.2f)spawnEnemy()}

        plants.forEach{p->p.timer+=dt;when(p.type){
            PlantType.SUN_BLOOM->{if(p.timer>5f){p.timer=0f;sunsDrop+=SunDrop(cx(p.col),cy(p.row)-35f,cy(p.row)-65f);sparkle(p.row,p.col,p.type.accent)}}
            PlantType.PEA_POD->{if(p.timer>.85f){p.timer=0f;bullets+=Bullet(p.row,cx(p.col)+28f,23f)}}
            PlantType.BURST_BERRY->{if(p.timer>2.4f){p.timer=0f;bullets+=Bullet(p.row,cx(p.col)+28f,55f,1)}}
            PlantType.WALL_BUD->Unit}}
        bullets.forEach{it.x+=if(it.kind==1)330f*dt else 410f*dt}
        val remove=HashSet<Bullet>()
        bullets.forEach{b->enemies.firstOrNull{it.row==b.row&&abs(it.x-b.x)<34f}?.let{e->e.hp-=b.damage;remove+=b;repeat(if(highGraphics)6 else 3){hit(e.x,cy(e.row),if(b.kind==1)Color.rgb(255,110,150) else Color.rgb(170,240,100))};if(b.kind==1)enemies.filter{it.row==b.row&&abs(it.x-b.x)<105f}.forEach{it.hp-=24f}}}
        bullets.removeAll(remove);bullets.removeAll{it.x>1180f}
        enemies.filter{it.hp<=0}.forEach{e->score+=if(e.type==1)60 else if(e.type==2)45 else 25;suns+=if(e.type==1)18 else 10;crowns=max(crowns,score/300);repeat(if(highGraphics)12 else 5){hit(e.x,cy(e.row),enemyColor(e.type))}}
        enemies.removeAll{it.hp<=0}
        enemies.forEach{e->e.bob+=dt*5f;e.attackTimer+=dt;val block=plants.firstOrNull{it.row==e.row&&abs(cx(it.col)-e.x)<48f};if(block==null)e.x-=e.speed*dt else if(e.attackTimer>.55f){e.attackTimer=0f;block.hp-=if(e.type==1)15f else if(e.type==2)6f else 9f;hit(cx(block.col),cy(block.row),Color.rgb(255,180,95))}}
        plants.removeAll{it.hp<=0}.toSet()
        val breached=enemies.filter{it.x<160f};if(breached.isNotEmpty()){breached.forEach{lives--;hit(155f,cy(it.row),Color.rgb(255,230,100))};enemies.removeAll(breached.toSet())}
        sunsDrop.forEach{it.pulse+=dt;it.life-=dt;it.y+=(it.targetY-it.y)*min(1f,dt*5f)};sunsDrop.removeAll{it.life<=0f}
        particles.forEach{it.x+=it.vx*dt;it.y+=it.vy*dt;it.vy+=80f*dt;it.life-=dt};particles.removeAll{it.life<=0}
        floatTexts.forEach{it.y-=25f*dt;it.life-=dt};floatTexts.removeAll{it.life<=0}
        if(lives<=0){gameOver=true;victory=false;screen=Screen.RESULT}
        if((mode=="ARENA"&&timeAlive>=60f)||(mode=="ADVENTURE"&&wave>=totalWaves&&timeAlive>45f)){gameOver=true;victory=true;screen=Screen.RESULT}
    }

    private fun spawnEnemy(){val roll=rand.nextFloat();val type=when{wave>=5&&roll<.13f->2;wave>=2&&roll<.3f->1;else->0};val hp=if(type==1)190f else if(type==2)65f else 85f;val speed=if(type==1)27f else if(type==2)72f else 39f;enemies+=Enemy(rand.nextInt(rows),1125f,type,hp,hp,speed)}
    private fun enemyColor(t:Int)=when(t){1->Color.rgb(194,122,67);2->Color.rgb(210,78,151);else->Color.rgb(110,205,102)}
    private fun sparkle(r:Int,c:Int,color:Int){hit(cx(c),cy(r),color)}
    private fun hit(x:Float,y:Float,color:Int){repeat(if(highGraphics)2 else 1){particles+=Particle(x,y,rand.nextFloat()*100f-50f,rand.nextFloat()*90f-55f,.6f,color,rand.nextFloat()*4f+3f)}}
    private fun toast(x:Float,y:Float,s:String,color:Int){floatTexts+=FloatText(x,y,s,1.2f,color)}

    override fun onDraw(c:Canvas){super.onDraw(c);drawWorld(c);when(screen){Screen.HOME->drawHome(c);Screen.MODE_SELECT->drawModes(c);Screen.BATTLE->drawBattle(c);Screen.PAUSE->{drawBattle(c);overlay(c);drawPause(c)};Screen.RESULT->{drawBattle(c);overlay(c);drawResult(c)};Screen.COLLECTION->drawCollection(c);Screen.SETTINGS->drawSettings(c)}}

    private fun drawWorld(c:Canvas){val g=LinearGradient(0f,0f,0f,height.toFloat(),Color.rgb(120,198,241),Color.rgb(222,241,181),Shader.TileMode.CLAMP);bg.shader=g;c.drawRect(0f,0f,width.toFloat(),height.toFloat(),bg);bg.shader=null;ui.color=Color.argb(150,255,250,210);c.drawCircle(X(1080f),Y(105f),X(42f),ui);ui.color=Color.rgb(89,161,91);val p=Path();p.moveTo(0f,Y(350f));p.cubicTo(X(210f),Y(245f),X(420f),Y(380f),X(640f),Y(300f));p.cubicTo(X(870f),Y(210f),X(1050f),Y(390f),X(1280f),Y(270f));p.lineTo(X(1280f),Y(720f));p.lineTo(0f,Y(720f));p.close();c.drawPath(p,ui);ui.color=Color.rgb(65,133,73);c.drawRect(0f,Y(390f),width.toFloat(),height.toFloat(),ui)}

    private fun card(c:Canvas,l:Float,t:Float,r:Float,b:Float,color:Int=Color.argb(230,18,49,29)){ui.color=color;c.drawRoundRect(X(l),Y(t),X(r),Y(b),X(24f),X(24f),ui);stroke.color=Color.argb(100,255,255,255);stroke.strokeWidth=X(1.5f);c.drawRoundRect(X(l),Y(t),X(r),Y(b),X(24f),X(24f),stroke)}
    private fun btn(c:Canvas,x:Float,y:Float,w:Float,h:Float,label:String,on:Boolean=true){ui.color=if(on)Color.rgb(227,190,73)else Color.rgb(82,97,83);c.drawRoundRect(X(x),Y(y),X(x+w),Y(y+h),X(17f),X(17f),ui);text.color=if(on)Color.rgb(46,39,20)else Color.LTGRAY;text.textSize=X(22f);c.drawText(label,X(x+24f),Y(y+h*.65f),text)}
    private fun heading(c:Canvas,a:String,b:String){text.color=Color.WHITE;text.textSize=X(53f);c.drawText(a,X(70f),Y(95f),text);normal.color=Color.rgb(209,232,204);normal.textSize=X(20f);c.drawText(b,X(74f),Y(127f),normal)}

    private fun drawHome(c:Canvas){card(c,45f,35f,1235f,685f,Color.argb(215,8,29,18));heading(c,"GARDEN CLASH","A magical garden tower-defense adventure");
        // decorative garden scene
        card(c,760f,155f,1190f,610f,Color.argb(170,30,76,43));text.color=Color.rgb(255,224,112);text.textSize=X(29f);c.drawText("THE GARDEN",X(815f),Y(205f),text)
        repeat(9){i->val xx=815f+i*38f;ui.color=Color.rgb(49,112,62);c.drawCircle(X(xx),Y(520f-(i%3)*12),X(22f),ui);ui.color=Color.rgb(83,151,73);c.drawRect(X(xx-3),Y(520f-(i%3)*12),X(xx+3),Y(585f),ui)}
        ui.color=Color.argb(70,255,240,130);c.drawCircle(X(1000f),Y(300f),X(72f),ui);ui.color=Color.rgb(255,225,100);c.drawCircle(X(1000f),Y(300f),X(48f),ui)
        btn(c,80f,175f,300f,66f,"ADVENTURE");btn(c,80f,255f,300f,66f,"ARENA");btn(c,80f,335f,300f,66f,"ALMANAC");btn(c,80f,415f,300f,66f,"SETTINGS");btn(c,80f,495f,300f,66f,"EXIT",false)
        text.color=Color.rgb(255,224,105);text.textSize=X(28f);c.drawText("SOIL LEAGUE",X(430f),Y(220f),text);normal.color=Color.WHITE;normal.textSize=X(20f);c.drawText("Crowns  $crowns",X(430f),Y(255f),normal);c.drawText("Plants  ${PlantType.entries.size}",X(430f),Y(285f),normal);c.drawText("Best score  $score",X(430f),Y(315f),normal)
        card(c,420f,355f,700f,590f,Color.argb(150,16,50,29));text.color=Color.rgb(216,246,186);text.textSize=X(23f);c.drawText("FEATURES",X(450f),Y(395f),text);normal.textSize=X(17f);normal.color=Color.rgb(230,240,215);listOf("• Wave-based defense","• Seed recharge","• Sun drops & plant food","• Enemy counters","• Arena survival").forEachIndexed{i,s->c.drawText(s,X(450f),Y(430f+i*28f),normal)}
    }

    private fun drawModes(c:Canvas){card(c,210f,65f,1070f,655f);heading(c,"SELECT BATTLE","Choose how you want to defend");btn(c,300f,190f,300f,78f,"ADVENTURE");btn(c,630f,190f,300f,78f,"ARENA");btn(c,300f,300f,300f,78f,"CHALLENGE",false);btn(c,630f,300f,300f,78f,"ENDLESS",false);normal.color=Color.rgb(210,235,202);normal.textSize=X(18f);c.drawText("Adventure: clear 8 waves and unlock the garden.",X(330f),Y(425f),normal);c.drawText("Arena: survive 60 seconds and chase Crowns.",X(330f),Y(460f),normal);btn(c,490f,535f,300f,60f,"BACK")}

    private fun drawBattle(c:Canvas){drawBattleTop(c);drawBoard(c);drawPlants(c);drawEnemies(c);drawBullets(c);drawSunDrops(c);drawParticles(c);drawTexts(c);drawSeedBank(c);if(flash>0){ui.color=Color.argb((flash*70).toInt(),255,225,120);c.drawRect(0f,0f,width.toFloat(),height.toFloat(),ui)}}

    private fun drawBattleTop(c:Canvas){ui.color=Color.argb(238,12,37,23);c.drawRect(0f,0f,width.toFloat(),Y(112f),ui);badge(c,26f,18f,145f,64f,Color.rgb(38,87,52),"☀  $suns",Color.rgb(255,225,105));badge(c,184f,18f,130f,64f,Color.rgb(42,76,48),"✦  $plantFood",Color.rgb(190,247,150));
        text.color=Color.WHITE;text.textSize=X(23f);c.drawText("WAVE $wave / $totalWaves",X(340f),Y(43f),text);normal.color=Color.rgb(194,226,191);normal.textSize=X(15f);c.drawText(if(mode=="ARENA")"60 SECOND ARENA" else "ADVENTURE GARDEN",X(340f),Y(67f),normal)
        ui.color=Color.argb(120,0,0,0);c.drawRoundRect(X(540f),Y(37f),X(900f),Y(58f),X(10f),X(10f),ui);ui.color=Color.rgb(121,205,93);val progress=(if(mode=="ARENA")timeAlive/60f else ((wave-1)+min(1f,waveTimer/18f))/totalWaves).coerceIn(0f,1f);c.drawRoundRect(X(540f),Y(37f),X(540f+360f*progress),Y(58f),X(10f),X(10f),ui)
        badge(c,920f,18f,130f,64f,Color.rgb(83,66,29),"♛ $crowns",Color.rgb(255,224,105));badge(c,1060f,18f,105f,64f,Color.rgb(35,77,48),"$score",Color.WHITE);btn(c,1180f,20f,70f,60f,"Ⅱ")}
    private fun badge(c:Canvas,x:Float,y:Float,w:Float,h:Float,col:Int,s:String,tc:Int){ui.color=col;c.drawRoundRect(X(x),Y(y),X(x+w),Y(y+h),X(18f),X(18f),ui);text.color=tc;text.textSize=X(19f);c.drawText(s,X(x+13f),Y(y+39f),text)}

    private fun drawBoard(c:Canvas){card(c,165f,126f,1135f,610f,Color.argb(150,32,91,48));for(r in 0 until rows)for(col in 0 until cols){val x=239f+col*92f;val y=140f+r*76f;ui.color=if((r+col)%2==0)Color.rgb(104,183,91)else Color.rgb(94,171,82);c.drawRoundRect(X(x),Y(y),X(x+88f),Y(y+72f),X(13f),X(13f),ui);ui.color=Color.argb(35,255,255,255);c.drawLine(X(x+8f),Y(y+12f),X(x+55f),Y(y+5f),ui)}
        // magical lane markers and gate
        for(r in 0 until rows){ui.color=Color.argb(80,255,236,125);c.drawCircle(X(190f),Y(cy(r)),X(15f),ui);ui.color=Color.rgb(238,208,118);c.drawRect(X(176f),Y(cy(r)-24f),X(184f),Y(cy(r)+24f),ui)}
        normal.color=Color.argb(125,255,255,255);normal.textSize=X(13f);c.drawText("ENEMY GATE",X(1020f),Y(132f),normal)
    }

    private fun drawSeedBank(c:Canvas){for(i in PlantType.entries.indices){val p=PlantType.entries[i];val x=18f;val y=135f+i*108f;ui.color=if(selected==p)Color.rgb(246,216,113) else Color.argb(235,18,48,28);c.drawRoundRect(X(x),Y(y),X(142f),Y(y+96f),X(18f),X(18f),ui);if(selected==p){stroke.color=Color.rgb(255,245,165);stroke.strokeWidth=X(3f);c.drawRoundRect(X(x+2),Y(y+2),X(140f),Y(y+94f),X(16f),X(16f),stroke)}
        drawPlantIcon(c,80f,y+43f,p);text.color=Color.WHITE;text.textSize=X(14f);c.drawText("$${p.cost}",X(51f),Y(y+87f),text);normal.textSize=X(12f);normal.color=Color.rgb(205,231,199);c.drawText(p.title.split(" ")[0],X(16f),Y(y+16f),normal);val cd=cooldown[i];if(cd>0){ui.color=Color.argb(155,0,0,0);c.drawRoundRect(X(x),Y(y),X(142f),Y(y+96f),X(18f),X(18f),ui);text.color=Color.WHITE;text.textSize=X(18f);c.drawText(String.format("%.1f",cd),X(55f),Y(y+55f),text)}}
        ui.color=if(shovel)Color.rgb(232,190,74)else Color.argb(235,18,48,28);c.drawRoundRect(X(18f),Y(575f),X(142f),Y(645f),X(18f),X(18f),ui);text.color=Color.WHITE;text.textSize=X(27f);c.drawText("⚒",X(48f),Y(620f),text);normal.textSize=X(12f);c.drawText("SHOVEL",X(78f),Y(618f),normal)
    }

    private fun drawSunDrops(c:Canvas){sunsDrop.forEach{s->val glow=14f+sin(s.pulse*4f).toFloat()*3f;ui.color=Color.argb(45,255,230,80);c.drawCircle(X(s.x),Y(s.y),X(glow+9),ui);ui.color=Color.rgb(255,221,65);c.drawCircle(X(s.x),Y(s.y),X(glow),ui);ui.color=Color.WHITE;c.drawCircle(X(s.x-4),Y(s.y-4),X(3f),ui)}}

    private fun drawPlants(c:Canvas){plants.forEach{p->val x=cx(p.col);val y=cy(p.row)+sin(timeAlive*2.2+p.col).toFloat()*2f;ui.color=Color.argb(70,20,45,20);c.drawOval(X(x-28f),Y(y+25f),X(x+28f),Y(y+37f),ui);drawPlantIcon(c,x,y,p.type);bar(c,x-28f,y-40f,56f,7f,p.hp/if(p.type==PlantType.WALL_BUD)180f else 100f,Color.rgb(101,224,113))}}
    private fun bar(c:Canvas,x:Float,y:Float,w:Float,h:Float,v:Float,col:Int){ui.color=Color.argb(170,15,30,18);c.drawRoundRect(X(x),Y(y),X(x+w),Y(y+h),X(4f),X(4f),ui);ui.color=col;c.drawRoundRect(X(x),Y(y),X(x+w*v.coerceIn(0f,1f)),Y(y+h),X(4f),X(4f),ui)}

    private fun drawEnemies(c:Canvas){enemies.forEach{e->val y=cy(e.row)+sin(e.bob).toFloat()*3f;ui.color=Color.argb(70,20,35,18);c.drawOval(X(e.x-28f),Y(y+28f),X(e.x+28f),Y(y+39f),ui);when(e.type){0->enemy(c,e.x,y,Color.rgb(110,92,112),Color.rgb(224,218,220),1f);1->enemy(c,e.x,y,Color.rgb(104,75,60),Color.rgb(190,150,108),1.25f);2->enemy(c,e.x,y,Color.rgb(170,68,133),Color.rgb(243,181,210),.9f)};bar(c,e.x-31f,y-45f,62f,7f,e.hp/e.maxHp,Color.rgb(230,95,89))}}
    private fun enemy(c:Canvas,x:Float,y:Float,body:Int,face:Int,scale:Float){ui.color=body;c.drawOval(X(x-22f*scale),Y(y-28f*scale),X(x+22f*scale),Y(y+29f*scale),ui);ui.color=face;c.drawOval(X(x-16f*scale),Y(y-17f*scale),X(x+16f*scale),Y(y+18f*scale),ui);ui.color=Color.rgb(48,32,45);c.drawCircle(X(x-7f*scale),Y(y-5f*scale),X(3.5f*scale),ui);c.drawCircle(X(x+7f*scale),Y(y-5f*scale),X(3.5f*scale),ui);ui.color=body;c.drawRect(X(x-18f*scale),Y(y+20f*scale),X(x-5f*scale),Y(y+39f*scale),ui);c.drawRect(X(x+5f*scale),Y(y+20f*scale),X(x+18f*scale),Y(y+39f*scale),ui)}

    private fun drawPlantIcon(c:Canvas,x:Float,y:Float,p:PlantType){when(p){PlantType.SUN_BLOOM->{ui.color=Color.rgb(79,151,70);c.drawRect(X(x-4),Y(y+5),X(x+4),Y(y+26),ui);repeat(8){i->val a=Math.PI*2*i/8;ui.color=Color.rgb(255,204,55);c.drawCircle(X(x+cos(a).toFloat()*16),Y(y+sin(a).toFloat()*16),X(8f),ui)};ui.color=Color.rgb(126,88,43);c.drawCircle(X(x),Y(y),X(8f),ui)};PlantType.PEA_POD->{ui.color=Color.rgb(54,144,72);c.drawOval(X(x-23),Y(y-17),X(x+21),Y(y+19),ui);ui.color=p.accent;c.drawCircle(X(x-7),Y(y+1),X(11),ui);c.drawCircle(X(x+8),Y(y),X(11),ui);ui.color=Color.WHITE;c.drawCircle(X(x-9),Y(y-5),X(3),ui);c.drawCircle(X(x+6),Y(y-5),X(3),ui)};PlantType.BURST_BERRY->{ui.color=Color.rgb(54,134,72);c.drawRect(X(x-4),Y(y+3),X(x+4),Y(y+25),ui);ui.color=p.color;c.drawCircle(X(x),Y(y-2),X(19),ui);ui.color=Color.WHITE;c.drawCircle(X(x-6),Y(y-6),X(3),ui);c.drawCircle(X(x+6),Y(y-6),X(3),ui)};PlantType.WALL_BUD->{ui.color=p.color;c.drawOval(X(x-20),Y(y-27),X(x+20),Y(y+27),ui);ui.color=p.accent;c.drawArc(X(x-15),Y(y-16),X(x+15),Y(y+19),220f,100f,true,ui);ui.color=Color.WHITE;c.drawCircle(X(x-6),Y(y-5),X(3),ui);c.drawCircle(X(x+6),Y(y-5),X(3),ui)}}}
    private fun drawBullets(c:Canvas){bullets.forEach{b->val y=cy(b.row);ui.color=if(b.kind==1)Color.rgb(255,110,155)else Color.rgb(166,240,95);c.drawCircle(X(b.x),Y(y),X(if(b.kind==1)11f else 7f),ui);ui.color=Color.WHITE;c.drawCircle(X(b.x-3),Y(y-3),X(3f),ui)}}
    private fun drawParticles(c:Canvas){particles.forEach{p->ui.color=p.color;ui.alpha=(255*(p.life/.6f).coerceIn(0f,1f)).toInt();c.drawCircle(X(p.x),Y(p.y),X(p.size),ui);ui.alpha=255}}
    private fun drawTexts(c:Canvas){normal.textSize=X(18f);floatTexts.forEach{f->normal.color=f.color;c.drawText(f.text,X(f.x),Y(f.y),normal)}}

    private fun overlay(c:Canvas){ui.color=Color.argb(185,4,16,9);c.drawRect(0f,0f,width.toFloat(),height.toFloat(),ui)}
    private fun drawPause(c:Canvas){card(c,400f,145f,880f,575f);text.color=Color.WHITE;text.textSize=X(48f);c.drawText("PAUSED",X(520f),Y(215f),text);btn(c,485f,260f,310f,62f,"RESUME");btn(c,485f,340f,310f,62f,"RESTART");btn(c,485f,420f,310f,62f,"QUIT")}
    private fun drawResult(c:Canvas){card(c,300f,100f,980f,630f);text.color=if(victory)Color.rgb(255,224,105)else Color.rgb(255,125,115);text.textSize=X(48f);c.drawText(if(victory)"GARDEN SECURED!" else "GARDEN FALLEN",X(405f),Y(190f),text);normal.color=Color.WHITE;normal.textSize=X(23f);c.drawText("Score     $score",X(470f),Y(250f),normal);c.drawText("Crowns    $crowns",X(470f),Y(290f),normal);c.drawText("Wave      $wave / $totalWaves",X(470f),Y(330f),normal);btn(c,425f,390f,230f,62f,"PLAY AGAIN");btn(c,675f,390f,190f,62f,"HOME")}

    private fun drawCollection(c:Canvas){card(c,220f,60f,1060f,660f);heading(c,"ALMANAC","Defenders, costs and tactical roles");PlantType.entries.forEachIndexed{i,p->val x=300f+(i%2)*340f;val y=175f+(i/2)*170f;card(c,x,y,x+300f,y+135f,Color.argb(145,24,64,38));drawPlantIcon(c,x+48f,y+65f,p);text.color=Color.WHITE;text.textSize=X(21f);c.drawText(p.title,X(x+85f),Y(y+45f),text);normal.color=Color.rgb(207,231,202);normal.textSize=X(15f);c.drawText("Sun ${p.cost}",X(x+85f),Y(y+72f),normal);c.drawText(p.desc,X(x+85f),Y(y+98f),normal)};btn(c,490f,570f,300f,60f,"BACK")}
    private fun drawSettings(c:Canvas){card(c,270f,60f,1010f,660f);heading(c,"SETTINGS","Tune the garden experience");val names=listOf("SFX","MUSIC","VIBRATION","HIGH GRAPHICS");val vals=listOf(sfx,music,vibration,highGraphics);names.forEachIndexed{i,n->val y=190f+i*75f;text.color=Color.WHITE;text.textSize=X(23f);c.drawText(n,X(370f),Y(y),text);btn(c,690f,y-35f,180f,55f,if(vals[i])"ON" else "OFF")};btn(c,490f,535f,300f,60f,"BACK")}

    override fun onTouchEvent(e:MotionEvent):Boolean{if(e.action!=MotionEvent.ACTION_UP)return true;val x=e.x/sx();val y=e.y/sy();when(screen){
        Screen.HOME->when{ x in 80f..380f&&y in 175f..241f->{mode="ADVENTURE";resetGame();screen=Screen.BATTLE};x in 80f..380f&&y in 255f..321f->{mode="ARENA";resetGame();screen=Screen.BATTLE};x in 80f..380f&&y in 335f..401f->screen=Screen.COLLECTION;x in 80f..380f&&y in 415f..481f->screen=Screen.SETTINGS }
        Screen.MODE_SELECT->{when{ x in 300f..600f&&y in 190f..268f->{mode="ADVENTURE";resetGame();screen=Screen.BATTLE};x in 630f..930f&&y in 190f..268f->{mode="ARENA";resetGame();screen=Screen.BATTLE};x in 490f..790f&&y in 535f..595f->screen=Screen.HOME}}
        Screen.BATTLE->{if(x>1160f&&y<100f){paused=true;screen=Screen.PAUSE;return true};if(x in 18f..142f&&y in 575f..645f){shovel=!shovel;return true};if(x<150f&&y>=135f&&y<567f){val i=((y-135f)/108f).toInt();if(i in PlantType.entries.indices)selected=PlantType.entries[i];shovel=false;return true};val col=((x-239f)/92f).toInt();val row=((y-140f)/76f).toInt();if(col !in 0 until cols||row !in 0 until rows)return true;if(shovel){plants.removeAll{it.row==row&&it.col==col};shovel=false;return true};val p=plants.firstOrNull{it.row==row&&it.col==col};if(p!=null&&plantFood>0){plantFood--;p.hp=min(if(p.type==PlantType.WALL_BUD)180f else 100f,p.hp+45f);toast(cx(col),cy(row)-30f,"PLANT FOOD!",Color.rgb(180,255,110));return true};if(suns>=selected.cost&&cooldown[selected.ordinal]<=0f){suns-=selected.cost;plants+=Plant(row,col,selected,if(selected==PlantType.WALL_BUD)180f else 100f);cooldown[selected.ordinal]=selected.cooldown;sparkle(row,col,selected.accent)}else if(suns<selected.cost)toast(cx(col),cy(row)-25f,"NEED SUN",Color.rgb(255,220,100))}
        Screen.PAUSE->when{ x in 485f..795f&&y in 260f..322f->{paused=false;screen=Screen.BATTLE};x in 485f..795f&&y in 340f..402f->{resetGame();paused=false;screen=Screen.BATTLE};x in 485f..795f&&y in 420f..482f->{paused=false;screen=Screen.HOME}}
        Screen.RESULT->when{x in 425f..655f&&y in 390f..452f->{resetGame();screen=Screen.BATTLE};x in 675f..865f&&y in 390f..452f->screen=Screen.HOME}
        Screen.COLLECTION->if(x in 490f..790f&&y in 570f..630f)screen=Screen.HOME
        Screen.SETTINGS->when{x in 690f..870f&&y in 155f..210f->sfx=!sfx;x in 690f..870f&&y in 230f..285f->music=!music;x in 690f..870f&&y in 305f..360f->vibration=!vibration;x in 690f..870f&&y in 380f..435f->highGraphics=!highGraphics;x in 490f..790f&&y in 535f..595f->screen=Screen.HOME}
    };return true}

    private fun resetGame(){plants.clear();enemies.clear();bullets.clear();particles.clear();floatTexts.clear();sunsDrop.clear();cooldown.fill(0f);suns=150;score=0;crowns=0;lives=3;plantFood=1;wave=1;waveTimer=0f;spawnTimer=0f;timeAlive=0f;selected=PlantType.PEA_POD;gameOver=false;victory=false;shovel=false;paused=false;lastNs=System.nanoTime()}
}
