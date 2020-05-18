function z = sph_tangency(z1,z2,r1,r2)
%z=sph_tangency(z1,z2,r1,r2,hes) 

len=s_dist(z1,z2);
z=s_shoot(z1,z2,len*r1/(r1+r2));

end